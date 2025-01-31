package com.yellastrodev

import com.yellastrodev.yLogger.AppLogger
import com.yellastrodev.ymtserial.KEY_PATH
import com.yellastrodev.ymtserial.PATH_BASE
import com.yellastrodev.ymtserial.ROUT_DOWNLOAD
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File

fun Application.configureUtilApiRouting() {

    val TAG = "UtilApi"

    routing {
        route("/api") {
            get("/$ROUT_DOWNLOAD") {
                AppLogger.info(TAG, "api/$ROUT_DOWNLOAD")
                try {
                    val params = extractParametersOrFail(call, listOf(KEY_PATH)) { errorMessage ->
                        call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                    } ?: return@get
                    val filePath = params[KEY_PATH]!!
                    AppLogger.info(TAG, "download path: $filePath")
                    val file = File(filePath)
                    if (file.exists()) {
                        call.respondFile(file)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Файл не найден")
                    }
                } catch (e: Exception) {
                    AppLogger.error(TAG, "api/$ROUT_DOWNLOAD", e)
                    call.respond(HttpStatusCode.InternalServerError, "Внутренняя ошибка сервера: \n${e.message}")
                }

            }

            get("/delayed-response") {
                // connection содержит информацию о текущем соединении, но не непосредственно сокет
//                    println("Connection info: $connection")
//
//                    // Ваш код задержки
//                    delay(30000) // 30 секунд
//                    call.respond(HttpStatusCode.OK, "Ответ после паузы 30 секунд")
                // Создаем OutputStreamContent для передачи данных
                val data = "This is the streamed data"

                // Создаем поток для данных
                val outputStream = ByteArrayOutputStream()
                outputStream.write(data.toByteArray())

                AppLogger.debug(TAG, "delayed-response outputStream = ${outputStream.toString()}")
                // Создаем OutputStreamContent
//                call.respond(
//                    OutputStreamContent(
//                        body = {
//                            var i = 0
//                            // Запись данных в OutputStream
//                            while (i < 3){
//                                delay(3000)
//                                val data = "This is the streamed data\n"
//                                write(data.toByteArray()) // Запись в поток
//                                AppLogger.debug(TAG, "data writed")
//                                i++
//                            }
//
//                        },
//                        contentType = ContentType.Text.Plain, // Тип контента
//                        contentLength = data.length.toLong() * 3 // Длина данных
//                    )
//                )
//                call.respond(
//                    ChannelWriterContent(
//                        channel = produce {
//                            send("Part of the content")
//                            send("Another part of the content")
//                        },
//                        contentType = ContentType.Text.Plain
//                    )
//                )
                try{
                    call.respondTextWriter(contentType = ContentType.Text.Plain) {
                        try {
                            var i = 0
                            // Запись данных в OutputStream
                            while (i < 3) {
                                delay(3000)
                                val data = ""
                                write(data) // Запись в поток
                                AppLogger.debug(TAG, "data writed")
                                i++
                            }
                            write("hello")
                        } catch (e: Exception) {
                            AppLogger.error(TAG, "delayed-response write stream", e)
                        }
                    }
                } catch (e: ChannelWriteException) {
                    AppLogger.error(TAG, "delayed-response ChannelWriteException")
                } catch (e: Exception) { // io.ktor.util.cio.ChannelWriteException: Cannot write to a channel
                    AppLogger.error(TAG, "delayed-response", e)
                }
                AppLogger.debug(TAG, "delayed-response end")
            }
        }
    }
}