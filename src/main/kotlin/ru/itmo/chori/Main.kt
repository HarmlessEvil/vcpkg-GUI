package ru.itmo.chori

import kotlinx.coroutines.*
import ru.itmo.chori.models.Package
import ru.itmo.chori.models.PackagesTableModel
import ru.itmo.chori.workers.CancellableBackgroundProcessWorker
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
    }
}

data class ProcessResult(val exitSuccess: Boolean, val text: String)
class ProcessExecutionException(override val message: String) : Exception(message)

fun AppWindow.makeOnButtonTest(root: JFrame): (ActionEvent) -> Unit {
    val vcpkgVersionRegex = """(Vcpkg package management program version .*\b)""".toRegex()

    return {
        coroutineUIScope.launch {
            buttonTest.isEnabled = false

            CancellableBackgroundProcessWorker(
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
    val spaceDelimitersRegex = " +".toRegex()

    return {
        coroutineUIScope.launch {
            refreshButton.isEnabled = false

            CancellableBackgroundProcessWorker(
                programArguments = listOf(vckpgPath.text, "list"),
                buttonToEnable = refreshButton,
                appWindow = this@makeOnButtonRefresh,
                dialogTitle = "Fetching vcpkg packages list",
                root = root,
                onDone = {
                    try {
                        val packages = get() ?: return@CancellableBackgroundProcessWorker
                        (tablePackages.model as PackagesTableModel).setPackages(packages)
                        textAreaStatus.text = ""
                    } catch (e: ExecutionException) {
                        val cause = e.cause as? ProcessExecutionException ?: return@CancellableBackgroundProcessWorker
                        textAreaStatus.text = cause.message
                    }
                }
            ) { process ->
                val res = emptyList<Package>().toMutableList()

                if (process.waitFor() != 0) {
                    throw ProcessExecutionException(String(process.inputStream.readAllBytes()))
                }

                process.inputStream.reader().forEachLine {
                    val (name, version, description) = it.split(spaceDelimitersRegex, 3)
                    res.add(Package(name, version, description))
                }

                res
            }.execute()
        }
    }
}
