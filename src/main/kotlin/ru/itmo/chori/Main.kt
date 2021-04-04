package ru.itmo.chori

import kotlinx.coroutines.*
import ru.itmo.chori.models.Package
import ru.itmo.chori.models.PackagesTableModel
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.SwingWorker
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

fun AppWindow.makeOnButtonTest(root: JFrame): (ActionEvent) -> Unit = handler@{
    coroutineUIScope.launch {
        this@makeOnButtonTest.buttonTest.isEnabled = false
        DeterminingVersionWorker(vckpgPath.text, root, this@makeOnButtonTest).execute()
    }
}

fun AppWindow.makeOnButtonRefresh(root: JFrame): (ActionEvent) -> Unit = handler@{
    coroutineUIScope.launch {
        this@makeOnButtonRefresh.refreshButton.isEnabled = false
        FetchPackageListWorker(this@makeOnButtonRefresh, root).execute()
    }
}

class DeterminingVersionWorker(
    private val executable: String,
    private val root: JFrame,
    private val appWindow: AppWindow
) : SwingWorker<ProcessResult?, Void>() {
    companion object {
        private val vcpkgVersionRegex = """(Vcpkg package management program version .*\b)""".toRegex()
    }

    override fun doInBackground(): ProcessResult? {
        var showModalJob: Job? = null
        var exitSuccess: Boolean
        val res: String

        try {
            val process = ProcessBuilder(executable, "version")
                .redirectErrorStream(true)
                .start()

            val dialog = CancellableProcessDialog("Determining vcpkg version").apply {
                setOnCancelAction {
                    this@DeterminingVersionWorker.cancel(true)
                }

                setLocationRelativeTo(root)
                pack()
            }

            showModalJob = appWindow.coroutineUIScope.launch {
                delay(300)
                dialog.isVisible = true
            }

            exitSuccess = process.waitFor() == 0
            showModalJob.cancel()
            dialog.dispose()

            res = String(process.inputStream.readNBytes(100))
        } catch (ignored: InterruptedException) {
            return null
        } catch (e: IOException) {
            val text = e.message ?: "Error occurred while determining vcpkg version"
            exitSuccess = false

            return ProcessResult(exitSuccess, text)
        } finally {
            showModalJob?.cancel()
        }

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

        return ProcessResult(exitSuccess, text)
    }

    override fun done() {
        try {
            val result = get() ?: return
            appWindow.statusMessage.text = result.text
            appWindow.statusMessage.foreground = if (result.exitSuccess) Color.BLACK else Color.RED
        } catch (ignored: CancellationException) {
        } catch (ignored: InterruptedException) { // App was closed
        } finally {
            appWindow.buttonTest.isEnabled = true
            root.pack()
        }
    }
}

class ProcessExecutionException(message: String) : Exception(message)
class FetchPackageListWorker(
    private val appWindow: AppWindow,
    private val root: JFrame
) : SwingWorker<List<Package>?, Void>() {
    companion object {
        private val spaceDelimitersRegex = " +".toRegex()
    }

    override fun doInBackground(): List<Package>? {
        var showModalJob: Job? = null
        val res = emptyList<Package>().toMutableList()

        try {
            val process = ProcessBuilder(appWindow.vckpgPath.text, "list")
                .redirectErrorStream(true)
                .start()

            val dialog = CancellableProcessDialog("Fetching vcpkg packages list").apply {
                setOnCancelAction {
                    this@FetchPackageListWorker.cancel(true)
                }

                setLocationRelativeTo(root)
                pack()
            }

            showModalJob = appWindow.coroutineUIScope.launch {
                delay(300)
                dialog.isVisible = true
            }

            if (process.waitFor() != 0) {
                throw ProcessExecutionException(String(process.inputStream.readAllBytes()))
            }

            process.inputStream.reader().forEachLine {
                val (name, version, description) = it.split(spaceDelimitersRegex, 3)
                res.add(Package(name, version, description))
            }
        } catch (ignored: InterruptedException) {
            return null
        } catch (e: IOException) {
            throw ProcessExecutionException(e.message ?: "Error occurred while fetching vcpkg version list")
        } finally {
            showModalJob?.cancel()
        }

        return res
    }

    override fun done() {
        try {
            val packages = get() ?: return
            (appWindow.tablePackages.model as PackagesTableModel).setPackages(packages)
            appWindow.textAreaStatus.text = ""
        } catch(e: ExecutionException) {
            val cause = e.cause as? ProcessExecutionException ?: return
            appWindow.textAreaStatus.text = cause.message
        } finally {
            appWindow.refreshButton.isEnabled = true
        }
    }
}
