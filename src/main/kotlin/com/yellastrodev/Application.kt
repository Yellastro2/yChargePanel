package com.yellastrodev

import com.yellastrodev.yLogger.AppLogger
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sun.rmi.server.Dispatcher
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths

fun main(args: Array<String>) {
    AppLogger.init( 1024 * 1024 * 5)
//    io.ktor.server.netty.EngineMain.main(args)

    val port = 8080
    val server = embeddedServer(Netty, port = port) {
        module()
    }

    // Логирование информации о сервере
    server.environment.monitor.subscribe(ApplicationStarted) {
        AppLogger.info("Main", "Server is running on http://localhost:${port}")
    }

    server.start(wait = true)
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
