package com.yellastrodev

//import com.yellastrodev.yLogger.AppLogger
import com.yellastrodev.ymtserial.ylogger.AppLogger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.netty.channel.ChannelOption
import java.io.File
import java.io.FileNotFoundException

fun main(args: Array<String>) {
    AppLogger.init( File("panelLogFiles"),1024 * 1024 * 5)
//    io.ktor.server.netty.EngineMain.main(args)

    val port = 8080
    val server = embeddedServer(Netty, port = port) {
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
        }
        module()
        ChannelOption.AUTO_CLOSE
    }

    // Логирование информации о сервере
    server.environment.monitor.subscribe(ApplicationStarted) {
        AppLogger.info("Server is running on http://localhost:${port}")
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
    configureControlApiRouting()
    configureUtilApiRouting()

}
