package ru.itmo.chori.workers

import ru.itmo.chori.AddPackageDialog
import ru.itmo.chori.AppWindow
import javax.swing.SwingWorker

class PackageInstallWorker(
    private val pkg: String,
    private val appWindow: AppWindow,
    private val addPackageDialog: AddPackageDialog
) : SwingWorker<Unit, String>() {
    override fun doInBackground() {
        try {
            val process = ProcessBuilder(appWindow.vckpgPath.text, "install", pkg)
                .redirectErrorStream(true)
                .start()

            run loop@{
                process.inputStream.reader().useLines {
                    for (line in it) {
                        if (isCancelled) {
                            return@loop
                        }

                        publish(line)
                    }
                }
            }

            if (!isCancelled) {
                process.waitFor()
            }
        } catch (ignored: InterruptedException) {
        }
    }

    override fun process(chunks: List<String>) {
        for (line in chunks) {
            addPackageDialog.textAreaInstallStatus.append(line)
            addPackageDialog.textAreaInstallStatus.append("\n")
        }
    }

    override fun done() {
        with(addPackageDialog) {
            buttonInstall.isEnabled = true
            buttonSearch.isEnabled = true

            isInstalling = false
        }
    }
}
