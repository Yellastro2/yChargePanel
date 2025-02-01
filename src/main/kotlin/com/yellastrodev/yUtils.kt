package com.yellastrodev

import com.yellastrodev.databases.entities.Station
import com.yellastrodev.ymtserial.logFileDateFormat
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

suspend fun extractParametersOrFail(
    call: RoutingCall,
    keys: List<String>,
    onError: suspend (String) -> Unit
): Map<String, String>? {
    val resultMap = mutableMapOf<String, String>()
    val missingKeys = mutableListOf<String>()

    val parameters = call.request.queryParameters
    val routParameters = call.parameters
    val recieveParams = if (call.request.httpMethod == HttpMethod.Post) {
        val contentType = call.request.contentType()
        when {
            contentType.match(ContentType.Application.FormUrlEncoded) -> {
                // Если запрос содержит application/x-www-form-urlencoded
                 call.receiveParameters()
            }

            else -> { Parameters.Empty }
        }
//        call.receiveParameters()
    } else {
        Parameters.Empty // Или можно просто пустой объект, если нет тела в запросе
    }

    for (key in keys) {
        val value = parameters[key] ?: routParameters[key] ?: recieveParams[key]
        if (value == null) {
            missingKeys.add(key)
        } else {
            resultMap[key] = value
        }
    }

    if (missingKeys.isNotEmpty()) {
        onError("Missing parameters: ${missingKeys.joinToString(", ")}")
        return null
    }

    return resultMap
}

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

fun getSerialNumbersForStation(station: Station): List<String> {
    val serialNumbers = mutableListOf<String>()

    // Проходим по всем слотам станции
    station.state.keys().forEach { slotId ->
        // Извлекаем данные по банку
        val slotData = station.state.getJSONObject(slotId)
        val bankId = slotData.optString("bankId") // Серийный номер пауэрбанка (bankId)
        if (bankId.isNotEmpty()) {
            serialNumbers.add(bankId)
        }
    }

    return serialNumbers
}

fun getChargedPowerbanksCount(station: Station): Int {
    var chargedCount = 0

    // Проходим по всем слотам станции
    station.state.keys().forEach { slotId ->
        // Извлекаем данные по банку
        val slotData = station.state.getJSONObject(slotId)
        val charge = slotData.optInt("charge", -1) // Заряд пауэрбанка в процентах

        // Проверяем, если заряд больше 90%
        if (charge >= 90) {
            chargedCount++
        }
    }

    return chargedCount
}