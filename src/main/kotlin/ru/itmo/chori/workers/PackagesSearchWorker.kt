package ru.itmo.chori.workers

import ru.itmo.chori.AddPackageDialog
import ru.itmo.chori.AppWindow
import javax.swing.SwingWorker

class PackagesSearchWorker(
    private val searchTerm: String,
    private val appWindow: AppWindow,
    private val addPackageDialog: AddPackageDialog
) : SwingWorker<Unit, String>() {
    companion object {
        val multipleSpaceRegex = " +".toRegex()
    }

    override fun doInBackground() {
        try {
            val process = ProcessBuilder(appWindow.vckpgPath.text, "search", searchTerm)
                .redirectErrorStream(true)
                .start()

            run loop@{
                process.inputStream.reader().useLines {
                    for (line in it) {
                        if (isCancelled) {
                            return@loop
                        }

                        val parts = line.split(multipleSpaceRegex)
                        if (parts.size < 2) {
                            return@loop // End of parsable data
                        }

                        publish(parts[0])
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
        addPackageDialog.addAndGetFoundPackagesCount(chunks.size)

        for (line in chunks) {
            addPackageDialog.comboBoxPackage.addItem(line)
        }
    }

    override fun done() {
        addPackageDialog.progressBarSearch.isIndeterminate = false
    }
}