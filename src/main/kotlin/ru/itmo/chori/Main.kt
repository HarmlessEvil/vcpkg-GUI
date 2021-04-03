package ru.itmo.chori

import kotlinx.coroutines.*
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.concurrent.CancellationException
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
    }.invokeOnCompletion {
        it?.let {
            return@invokeOnCompletion
        }

        DeterminingVersionWorker(vckpgPath.text, root, this@makeOnButtonTest).execute()
    }
}

class DeterminingVersionWorker(
    private val executable: String,
    private val root: JFrame,
    private val appWindow: AppWindow
) : SwingWorker<ProcessResult?, Void>() {
    private val vcpkgVersionRegex = """(Vcpkg package management program version .*\b)""".toRegex()

    override fun doInBackground(): ProcessResult? {
        var showModalJob: Job? = null
        var exitSuccess: Boolean
        var res: String

        try {
            val process = ProcessBuilder(executable, "version")
                .redirectErrorStream(true)
                .start()

            val dialog = DeterminingVersionDialog().apply {
                setOnCancelAction {
                    this@DeterminingVersionWorker.cancel(true)
                }

                setLocationRelativeTo(root)
                pack()
            }

            showModalJob = appWindow.coroutineDefaultScope.launch {
                delay(300)
                dialog.isVisible = true
            }

            exitSuccess = process.waitFor() == 0
            showModalJob.cancel()
            dialog.dispose()

            res = String(process.inputStream.readNBytes(100))
        } catch (e: InterruptedException) {
            showModalJob?.cancel()
            return null
        }

        if (exitSuccess) {
            val matcher = vcpkgVersionRegex.find(res)
            if (matcher == null) {
                exitSuccess = false
                res = "Unsupported format for vcpkg version output: $res"
            } else {
                res = matcher.groupValues[1]
            }
        }

        return ProcessResult(exitSuccess, res)
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
