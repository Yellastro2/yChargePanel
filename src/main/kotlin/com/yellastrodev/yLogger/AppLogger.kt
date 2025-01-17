package com.yellastrodev.yLogger

import java.io.*
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AppLogger {
    var logger: Logger? = null
    const val DEBUG = true

    fun init(maxFileSize: Long = 1024 * 1024) {
        if (logger == null) {
            logger = Logger(maxFileSize)
        }
    }

    fun info(tag: String, message: String) {
        logger?.info(tag, message)
    }

    fun debug(tag: String, message: String) {
        if (DEBUG) logger?.info(tag, message)
    }

    fun warn(tag: String, message: String) {
        logger?.warn(tag, message)
    }

    fun error(tag: String, message: String, e: Exception? = null) {
        logger?.error(tag, message, e)
    }

    fun getZipLogs(fLastFileName: String): File {
        val filesDir = File(".")
        val files = filesDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val zipFile = File(filesDir, "archive.zip")
        zipFiles(files, zipFile, fLastFileName)
        return zipFile
    }

    private fun zipFiles(files: List<File>, zipFile: File, lastFileName: String) {
        if (zipFile.exists()) zipFile.delete()

        val lastFileDate = if (lastFileName == "0") Date(0)
        else Logger.logFileDateFormat.parse(lastFileName.substring(4, lastFileName.length - 4))

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
            files.filter { file ->
                if (file.name == "app.log") true
                else if (!file.name.startsWith("app")) false
                else {
                    val fileName = file.name.replace(" ", "_").replace(":", "-")
                    val fileDate = Logger.logFileDateFormat.parse(fileName.substring(4, file.name.length - 4))
                    fileDate.after(lastFileDate)
                }
            }.forEach { file ->
                FileInputStream(file).use { fi ->
                    BufferedInputStream(fi).use { origin ->
                        val entry = ZipEntry(file.name.replace(" ", "_").replace(":", "-"))
                        out.putNextEntry(entry)
                        origin.copyTo(out, 1024)
                    }
                }
            }
        }
    }

    fun getLastLogFile(): File? {
        return logger?.logFile
    }

    fun clearLastLog() {
        getLastLogFile()?.let { logFile ->
            if (logFile.exists()) logFile.delete()
        }
    }
}
