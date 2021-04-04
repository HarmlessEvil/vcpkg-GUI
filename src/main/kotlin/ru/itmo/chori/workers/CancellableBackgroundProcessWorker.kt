package ru.itmo.chori.workers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.itmo.chori.*
import java.io.IOException
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.SwingWorker

class CancellableBackgroundProcessWorker<T>(
    private val dialogTitle: String,
    private val programArguments: List<String>,

    private val root: JFrame,
    private val appWindow: AppWindow,
    private val buttonToEnable: JButton,

    private val onDone: CancellableBackgroundProcessWorker<T>.() -> Unit,
    private val outputParser: (Process) -> T
) : SwingWorker<T?, Void>() {
    override fun doInBackground(): T? {
        var dialog: CancellableProcessDialog? = null
        var showModalJob: Job? = null

        try {
            val process = ProcessBuilder(programArguments)
                .redirectErrorStream(true)
                .start()

            dialog = CancellableProcessDialog(dialogTitle).apply {
                setOnCancelAction {
                    this@CancellableBackgroundProcessWorker.cancel(true)
                }

                setLocationRelativeTo(root)
                pack()
            }

            showModalJob = appWindow.coroutineUIScope.launch {
                delay(300)
                dialog.isVisible = true
            }

            return outputParser(process)
        } catch (ignored: InterruptedException) {
            return null
        } catch (e: IOException) {
            throw ProcessExecutionException(e.message ?: "Error occurred while ${dialogTitle.toLowerCase()}")
        } finally {
            showModalJob?.cancel()
            dialog?.dispose()
        }
    }

    override fun done() {
        try {
            onDone()
        } catch (ignored: CancellationException) {
        } catch (ignored: InterruptedException) {
        } finally {
            buttonToEnable.isEnabled = true
        }
    }
}