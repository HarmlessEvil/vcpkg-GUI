package ru.itmo.chori

import com.beust.klaxon.JsonReader
import kotlinx.coroutines.*
import ru.itmo.chori.models.Package
import ru.itmo.chori.models.PackagesTableModel
import ru.itmo.chori.workers.CancellableBackgroundProcessWorker
import ru.itmo.chori.workers.PackageInstallWorker
import ru.itmo.chori.workers.PackagesSearchWorker
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.concurrent.ExecutionException
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.filechooser.FileFilter

private object ExecutableFilter : FileFilter() {
    override fun accept(f: File) = f.canExecute()
    override fun getDescription() = "Executables"
}

fun main() {
    val fileChooser = JFileChooser().apply {
        addChoosableFileFilter(ExecutableFilter)
    }

    CoroutineScope(Dispatchers.Main).launch {
        JFrame("vcpkg GUI").apply {
            val appWindow = AppWindow().also {
                it.vcpkgFileChooser.addActionListener(it.makeOnChooseFile(this, fileChooser))
                it.buttonTest.addActionListener(it.makeOnButtonTest(this))
                it.refreshButton.addActionListener(it.makeOnButtonRefresh(this))
                it.buttonRemove.addActionListener(it.makeOnButtonRemove(this))
                it.buttonAdd.addActionListener(it.makeOnButtonAdd(this))
            }

            addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    appWindow.coroutineDefaultScope.cancel()
                    appWindow.coroutineUIScope.cancel()
                    appWindow.coroutineIOScope.cancel()
                }
            })

            contentPane = appWindow.contentPane
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE

            setLocationRelativeTo(null)

            pack()

            isVisible = true
        }
    }
}

val AppWindow.coroutineDefaultScope: CoroutineScope by lazy { CoroutineScope(Dispatchers.Default) }
val AppWindow.coroutineUIScope: CoroutineScope by lazy { CoroutineScope(Dispatchers.Main) }
val AppWindow.coroutineIOScope: CoroutineScope by lazy { CoroutineScope(Dispatchers.IO) }

fun AppWindow.makeOnChooseFile(root: JFrame, fileChooser: JFileChooser): (ActionEvent) -> Unit = handler@{
    fileChooser.showDialog(root, "Choose").takeIf {
        it == JFileChooser.APPROVE_OPTION
    } ?: return@handler

    coroutineUIScope.launch {
        this@makeOnChooseFile.vckpgPath.text = fileChooser.selectedFile.path
        refreshButton.doClick()
    }
}

data class ProcessResult(val exitSuccess: Boolean, val text: String)
class ProcessExecutionException(override val message: String) : Exception(message)

fun AppWindow.makeOnButtonTest(root: JFrame): (ActionEvent) -> Unit {
    val vcpkgVersionRegex = """(Vcpkg package management program version .*\b)""".toRegex()

    return {
        coroutineUIScope.launch {
            buttonTest.isEnabled = false

            CancellableBackgroundProcessWorker<ProcessResult>(
                programArguments = listOf(vckpgPath.text, "version"),
                buttonToEnable = buttonTest,
                appWindow = this@makeOnButtonTest,
                dialogTitle = "Determining vcpkg version",
                root = root,
                onDone = {
                    try {
                        val result = get() ?: return@CancellableBackgroundProcessWorker

                        statusMessage.text = result.text
                        if (result.exitSuccess) {
                            statusMessage.foreground = Color.BLACK
                            refreshButton.doClick()

                            return@CancellableBackgroundProcessWorker
                        }

                        statusMessage.foreground = Color.RED
                    } catch (e: ExecutionException) {
                        val cause = e.cause as? ProcessExecutionException ?: return@CancellableBackgroundProcessWorker

                        statusMessage.text = cause.message
                        statusMessage.foreground = Color.RED
                    }
                }
            ) { process ->
                if (isCancelled) {
                    return@CancellableBackgroundProcessWorker null
                }

                var exitSuccess = process.waitFor() == 0
                val res = String(process.inputStream.readNBytes(100))

                var text: String = res
                if (res == "") {
                    text = "Empty vcpkg version output"
                    exitSuccess = false
                }

                if (exitSuccess) {
                    val matcher = vcpkgVersionRegex.find(res)
                    if (matcher == null) {
                        exitSuccess = false
                        text = "Unsupported format for vcpkg version output: $res"
                    } else {
                        text = matcher.groupValues[1]
                    }
                }

                ProcessResult(exitSuccess, text)
            }.execute()
        }
    }
}

fun AppWindow.makeOnButtonRefresh(root: JFrame): (ActionEvent) -> Unit {
    return {
        coroutineUIScope.launch {
            refreshButton.isEnabled = false

            CancellableBackgroundProcessWorker<List<Package>>(
                programArguments = listOf(vckpgPath.text, "list", "--x-json"), // It's the only one option that supports JSON output
                buttonToEnable = refreshButton,
                appWindow = this@makeOnButtonRefresh,
                dialogTitle = "Fetching vcpkg packages list",
                root = root,
                onDone = {
                    try {
                        val packages = get() ?: return@CancellableBackgroundProcessWorker
                        (tablePackages.model as PackagesTableModel).setPackages(packages)

                        if (packages.isEmpty()) {
                            textAreaStatus.text = "No packages installed"
                            textAreaStatus.foreground = Color.BLACK
                        } else {
                            textAreaStatus.text = ""
                        }
                    } catch (e: ExecutionException) {
                        val cause = e.cause as? ProcessExecutionException ?: return@CancellableBackgroundProcessWorker
                        textAreaStatus.text = "Error on refresh: " + cause.message
                        textAreaStatus.foreground = Color.RED
                    }
                }
            ) { process ->
                val res = emptyList<Package>().toMutableList()

                if (isCancelled) {
                    return@CancellableBackgroundProcessWorker null
                }

                if (process.waitFor() != 0) {
                    throw ProcessExecutionException(String(process.inputStream.readAllBytes()))
                }

                run loop@{
                    JsonReader(process.inputStream.reader()).use { reader ->
                        reader.beginObject {
                            while (reader.hasNext()) {
                                if (isCancelled) {
                                    return@beginObject
                                }

                                reader.nextName() // read package descriptor –- name:triplet
                                val pkg = reader.beginObject {
                                    var name: String? = null
                                    var version: String? = null
                                    var description: String? = null

                                    while (name == null || version == null || description == null) {
                                        when(reader.nextName()) { // IMO It should skip value and return next name and not fail
                                            "package_name" -> name = reader.nextString()
                                            "version" -> version = reader.nextString()
                                            "features" -> reader.nextArray()
                                            "desc" -> description = reader.nextArray().joinToString("\n")
                                            "port_version" -> reader.nextInt()
                                            else -> reader.nextString()
                                        }
                                    }

                                    while (reader.hasNext()) { // Consume the rest of object if any. Otherwise it will fail -_-
                                        when(reader.nextName()) {
                                            "features" -> reader.nextArray()
                                            "desc" -> reader.nextArray()
                                            "port_version" -> reader.nextInt()
                                            else -> reader.nextString()
                                        }

                                    }

                                    Package(name, version, description)
                                }

                                res.add(pkg)
                            }
                        }
                    }
                }

                res
            }.execute()
        }
    }
}

fun AppWindow.makeOnButtonRemove(root: JFrame): (ActionEvent) -> Unit = handler@{
    if (tablePackages.selectedRow == -1) {
        return@handler
    }

    val pkg = tablePackages.getValueAt(tablePackages.selectedRow, 0).toString()

    coroutineUIScope.launch {
        buttonRemove.isEnabled = false

        CancellableBackgroundProcessWorker<ProcessResult>(
            programArguments = listOf(vckpgPath.text, "remove", pkg),
            buttonToEnable = buttonRemove,
            appWindow = this@makeOnButtonRemove,
            dialogTitle = "Removing package $pkg",
            root = root,
            onDone = {
                try {
                    val result = get() ?: return@CancellableBackgroundProcessWorker

                    if (!result.exitSuccess) {
                        textAreaStatus.text = result.text

                        return@CancellableBackgroundProcessWorker
                    }

                    refreshButton.doClick()
                    textAreaStatus.text = ""
                } catch (e: ExecutionException) {
                    val cause = e.cause as? ProcessExecutionException ?: return@CancellableBackgroundProcessWorker
                    textAreaStatus.text = cause.message
                }
            }
        ) { process ->
            if (isCancelled) {
                return@CancellableBackgroundProcessWorker null
            }

            var exitSuccess = process.waitFor() == 0 // It always returns 0. This approach of determining success is
            // very unreliable and not stable. But there is no alternative for now
            val res = String(process.inputStream.readAllBytes())

            var text: String = res
            if (res == "") {
                text = "Empty vcpkg remove output"
                exitSuccess = false
            }

            if (exitSuccess) {
                if (!res.trim().endsWith("done")) {
                    exitSuccess = false
                    text = res
                } else {
                    text = ""
                }
            }

            ProcessResult(exitSuccess, text)
        }.execute()
    }
}

fun AppWindow.makeOnButtonAdd(root: JFrame): (ActionEvent) -> Unit = {
    coroutineUIScope.launch {
        AddPackageDialog().apply {
            setLocationRelativeTo(root)
            pack()

            addPropertyChangeListener {
                labelFound.text = "Found: " + it.newValue as Int
                pack()
            }

            buttonSearch.addActionListener(this.makeOnButtonSearch(this@makeOnButtonAdd))
            buttonInstall.addActionListener(this.makeOnButtonInstall(this@makeOnButtonAdd))

            setOnClose {
                coroutineUIScope.launch {
                    refreshButton.doClick()
                }
            }

            isVisible = true
        }
    }
}

fun AddPackageDialog.makeOnButtonSearch(appWindow: AppWindow): (ActionEvent) -> Unit = {
    val text = comboBoxPackage.editor.item.toString()
    setFoundPackagesCount(0)

    appWindow.coroutineUIScope.launch {
        comboBoxPackage.removeAllItems()
        progressBarSearch.isIndeterminate = true

        PackagesSearchWorker(
            searchTerm = text,
            appWindow = appWindow,
            addPackageDialog = this@makeOnButtonSearch
        ).execute()
    }
}

fun AddPackageDialog.makeOnButtonInstall(appWindow: AppWindow): (ActionEvent) -> Unit = handler@{
    val pkg = comboBoxPackage.selectedItem?.toString() ?: return@handler

    isInstalling = true
    appWindow.coroutineUIScope.launch {
        buttonInstall.isEnabled = false
        buttonSearch.isEnabled = false
        textAreaInstallStatus.text = ""

        PackageInstallWorker(
            pkg = pkg,
            appWindow = appWindow,
            addPackageDialog = this@makeOnButtonInstall
        ).execute()
    }
}
