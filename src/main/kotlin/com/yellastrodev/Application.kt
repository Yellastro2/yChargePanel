package com.yellastrodev

import com.yellastrodev.yLogger.AppLogger
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths

fun main(args: Array<String>) {
    AppLogger.init( 1024 * 1024 * 5)
    io.ktor.server.netty.EngineMain.main(args)
}


fun readHtml(filename: String): String {
    val resource = object {}.javaClass.getResourceAsStream("/templates/$filename")
    return resource?.bufferedReader()?.use { it.readText() } ?: throw FileNotFoundException("Resource /templates/$filename not found")
}





fun Application.module() {
    configureSecurity()
    configureWebApiRouting()
    configureWebRouting()
    configureStationRouting()

}
