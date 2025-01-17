package com.yellastrodev.yLogger

import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class Logger(private val maxFileSize: Long = 1024 * 1024) { // 1 MB по умолчанию

    companion object {
        const val BASE_LOG_FILENAME = "app.log"
        val logFileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    var logFile = File(BASE_LOG_FILENAME)
    private val customHandlers = mutableListOf<(String) -> Unit>()

    fun addLogHandler(handler: (String) -> Unit) {
        customHandlers.add(handler)
    }

    fun info(tag: String, message: String) {
        log("INFO", tag, message)
    }

    fun warn(tag: String, message: String) {
        log("WARN", tag, message)
    }

    fun error(tag: String, message: String, e: Exception? = null) {
        log("ERROR", tag, message, e)
    }

    private fun log(level: String, tag: String, message: String, e: Exception? = null) {
        val currentDate = logFileDateFormat.format(Date())
        var logMessage = "$currentDate $level/$tag: $message"
        e?.let { logMessage += "\n${it.stackTraceToString()}" }
        logToFile(logMessage)
        logToConsole(currentDate, level, tag, message, e)
        customHandlers.forEach { it(logMessage) }
    }

    private fun logToFile(message: String) {
        rotateLogFileIfNeeded()
        try {
            val writer = FileWriter(logFile, true)
            writer.append(message).append("\n")
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }



    private fun logToConsole(time: String, level: String, tag: String, message: String, e: Exception? = null) {
        
        System.out.println("Debug: null fluscher")
        when (level) {
            "INFO" -> print("$time INFO: $tag: $message")
            "WARN" -> print("$time WARN: $tag: $message")
            "ERROR" -> e?.let { print("$time ERROR: $tag: $message\n${it.stackTraceToString()}") } ?: print("$time ERROR: $tag: $message")
            else -> print("$time DEBUG: $tag: $message")
        }
    }


    private fun rotateLogFileIfNeeded() {
        if (logFile.length() >= maxFileSize) {
            val newFileName = "app_${logFileDateFormat.format(Date())}.log"
            val newLogFile = File(newFileName)
            logFile.renameTo(newLogFile)
            logFile = File(BASE_LOG_FILENAME)
        }
    }
}
