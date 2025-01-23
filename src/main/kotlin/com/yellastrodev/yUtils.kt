package com.yellastrodev

import com.yellastrodev.ymtserial.logFileDateFormat
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

fun extractZip(zipFile: File, outputDir: File) {
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    ZipInputStream(zipFile.inputStream()).use { zis ->
        var entry: ZipEntry?
        while (zis.nextEntry.also { entry = it } != null) {
            val newFile = File(outputDir, entry!!.name)
            if (entry!!.isDirectory) {
                newFile.mkdirs()
            } else {
                newFile.parentFile.mkdirs()
                newFile.outputStream().use { fos ->
                    zis.copyTo(fos)
                }
            }
        }
    }
}

fun getLastLogZip(stId: String): File {
    val uploadDir = File("${PATH_LOGFILES}/${stId}")
    val zipFile = File(uploadDir, "logs_upload_${stId}.zip")
    val files = uploadDir.listFiles()?.toList() ?: emptyList()
    zipFiles(files, zipFile)
    return zipFile
}

fun zipFiles(files: List<File>, zipFile: File) {
    if (zipFile.exists()) {
        zipFile.delete()
    }

    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
        files.filter { file ->
            (file.name.endsWith(".log") )
        }.forEach { file ->
            FileInputStream(file).use { fi ->
                BufferedInputStream(fi).use { origin ->
                    val entry = ZipEntry(file.name.replace(" ","_").replace(":","-"))
                    out.putNextEntry(entry)
                    origin.copyTo(out, 1024)
                }
            }
        }
    }
}

fun findLatestLogFile(dir: File): File? {
    var latestFile: File? = null
    var latestDate: Long = 0L

    dir.listFiles()?.forEach { file ->
        val name = file.name
        if (name.startsWith("app_") && name.endsWith(".log")) {
            try {
                val dateString = name.substring(4, name.length - 4)
                val date = logFileDateFormat.parse(dateString)
                if (date != null && date.time > latestDate) {
                    latestDate = date.time
                    latestFile = file
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    return latestFile
}