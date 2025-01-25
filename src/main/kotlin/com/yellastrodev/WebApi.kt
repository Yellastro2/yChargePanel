package com.yellastrodev

import com.yellastrodev.CommandsManager.isClientConnected
import com.yellastrodev.CommandsManager.sendCommandToStation
import com.yellastrodev.CommandsManager.setWallpaper
import com.yellastrodev.ymtserial.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.concurrent.CompletableFuture



@Serializable
data class CheckinData(val stId: String, val size: Int, val state: String)

fun Application.configureWebApiRouting() {

    val TIMEOUT_LOG_UPLOAD = 60L

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }


    // Карта для хранения колбеков
    val uploadLogsCallbacks = mutableMapOf<String, CompletableFuture<Unit>>()

    routing {
        route("/api") {
            get("/$ROUT_RELEASE") {
                val params = extractParametersOrFail(call, listOf(KEY_STATION_ID,KEY_NUM)) { errorMessage ->
                    call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                } ?: return@get

                val stId = params[KEY_STATION_ID]!!
                val num = params[KEY_NUM]!!

                sendCommandToStation(stId, JSONObject(mapOf(CMD_RELEASE to num)))
                call.respond(HttpStatusCode.OK, """{"status": "released"}""")
            }





            get("/$ROUT_DOWNLOAD") {
                val params = extractParametersOrFail(call, listOf(KEY_PATH)) { errorMessage ->
                    call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                } ?: return@get
                val filePath = params[KEY_PATH]!!
                val file = File("$PATH_BASE/$filePath")
                if (file.exists()) {
                    call.respondFile(file)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Файл не найден")
                }
            }

            post("/$ROUT_UPLOAD_WALLPAPER/{$KEY_STATION_ID}") {
                val params = extractParametersOrFail(call, listOf(KEY_STATION_ID)) { errorMessage ->
                    call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                } ?: return@post
                val stId = params[KEY_STATION_ID]!!
                val uploadDir = File(PATH_WALLPAPERS)
                if (!uploadDir.exists()) {
                    uploadDir.mkdirs()
                }

                // Найти максимальный номер файла
                val maxFileNumber = uploadDir.listFiles()?.mapNotNull {
                    it.name.substringBeforeLast(".").toIntOrNull()
                }?.maxOrNull() ?: 0
                val newFileName = "${maxFileNumber + 1}.jpg"

                val multipart = call.receiveMultipart()
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val fileBytes = part.provider.invoke().readRemaining().readByteArray()
                        File(uploadDir, newFileName).writeBytes(fileBytes)
                    }
                    part.dispose()
                }

                setWallpaper(stId, newFileName)

                call.respondRedirect("/station/${stId}")
            }






            post("/$ROUT_SET_QR/{stId}") {
                val params = extractParametersOrFail(call, listOf(KEY_STATION_ID,KEY_TEXT)) { errorMessage ->
                    call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                } ?: return@post
                val stId = params[KEY_STATION_ID]!!

                try {

                    val QRString = params[KEY_TEXT]!!

                    val fStation = database.getStationById(stId)
                        ?: return@post call.respondText("Station with ID $stId not found.", status = HttpStatusCode.NotFound)

                    if (fStation.qrString != QRString) {
                        fStation.qrString = QRString
                        database.updateStation(fStation)
                        sendCommandToStation(stId, JSONObject(mapOf(CMD_CHANGE_QR to QRString)))
                    }


                    call.respondRedirect("$ROUT_STATION/${stId}")
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respondRedirect("$ROUT_STATION/${stId}")
                }
            }



            get("/$ROUT_GET_LOGS") {
                val params = extractParametersOrFail(call, listOf(KEY_STATION_ID)) { errorMessage ->
                    call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                } ?: return@get

                val stId = params[KEY_STATION_ID]!!
                val uploadDir = File("${PATH_LOGFILES}/${stId}")
                val lastLogFile = findLatestLogFile(uploadDir)?.name ?: "0"
                if (stId != null) {
                    // TODO_1
                    // Проверяем, на связи ли клиент
                    val isClientConnected = isClientConnected(stId)
                    if (isClientConnected) {
                        // Создаем CompletableFuture для загрузки логов
                        val uploadFuture = CompletableFuture<Unit>()
                        uploadLogsCallbacks[stId] = uploadFuture

                        // Отправляем команду на получение логов
                        sendCommandToStation(stId, JSONObject(mapOf(CMD_GETLOGS to lastLogFile)))
//                    waitMap[stId]?.complete(JsonObject(mapOf(CMD_GETLOGS to JsonPrimitive(lastLogFile))))

                        // Ожидаем загрузки логов
                        val uploadCompleted = withTimeoutOrNull(TIMEOUT_LOG_UPLOAD * 1000) {
                            uploadLogsCallbacks[stId]?.await()
                        }
                        if (uploadCompleted != null) {
                            print("""{"status": "logs uploaded"}""")
//                        call.respond(HttpStatusCode.OK, """{"status": "logs uploaded"}""")
                        } else {
                            print("""{"status": "upload timeout"}""")
//                        call.respond(HttpStatusCode.RequestTimeout, """{"status": "upload timeout"}""")
                        }
                    } else {
                        // Клиент не на связи, сразу отвечаем
                        print("""{"status": "client not connected"}""")
//                    call.respond(HttpStatusCode.OK, """{"status": "client not connected"}""")
                    }
                    val zipFile = getLastLogZip(stId)
                    call.respondFile(zipFile)
//                waitMap[stId]?.complete(JsonObject(mapOf(CMD_GETLOGS to JsonPrimitive(lastLogFile))))
//                call.respond(HttpStatusCode.OK, """{"status": "getting.."}""")
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing 'stId'")
                }
            }

            get("/$ROUT_STATIONLIST") {
                try {
                    val fStations = database.getStations()

                    val jsonArray = JSONArray().apply {
                        fStations.forEach { station ->
                            // Создаем JSONObject прямо из полей объекта Station
                            val jsonObject = JSONObject().apply {
                                put(KEY_STATION_ID, station.stId)
                                put(KEY_SIZE, station.size)
                                put(KEY_AVAIBLE, station.state.length())  // Количество полей в state
                                put(KEY_TIMESTAMP, station.timestamp)
                                put(KEY_TRAFFIC_LAST_DAY, station.lastDayTraffic)
                            }
                            this.put(jsonObject) // Добавляем объект в JSONArray
                        }
                    }

                    call.respond(HttpStatusCode.OK, jsonArray.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }

            get("/$ROUT_STATIONINFO") {
                try {
                    val params = extractParametersOrFail(call, listOf(KEY_STATION_ID)) { errorMessage ->
                        call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                    } ?: return@get

                    val stId = params[KEY_STATION_ID]!!

                    val fStation = database.getStationById(stId)
                        ?: return@get call.respondText("Station with ID $stId not found.", status = HttpStatusCode.NotFound)

                    // Создаем JSONObject напрямую из данных fStation
                    val fStationJson = JSONObject().apply {
                        put(KEY_STATION_ID, fStation.stId)
                        put(KEY_SIZE, fStation.size)
                        put(KEY_STATE, fStation.state)  // Уже JSONObject
                        put(KEY_TIMESTAMP, fStation.timestamp)
                        put(KEY_TRAFFIC_LAST_DAY, fStation.lastDayTraffic)
                        // Проверяем наличие событий и добавляем их в JSON
                        if (fStation.events.isNotEmpty()) {
                            put(KEY_EVENT, JSONArray(fStation.events))
                        } else {
                            put(KEY_EVENT, JSONArray())  // Если событий нет, добавляем пустой массив
                        }
                    }
                    call.respond(HttpStatusCode.OK, fStationJson.toString())



//                call.respond(HttpStatusCode.OK, jsonArray.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            post("/$ROUT_UPLOADLOGS") {
                val stId = call.request.queryParameters.get(KEY_STATION_ID)
                val multipart = call.receiveMultipart()
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val fileBytes = part.provider.invoke().readRemaining().readByteArray()
                        val uploadDir = File("${PATH_LOGFILES}/${stId}")
                        if (!uploadDir.exists()) {
                            uploadDir.mkdirs() // создаем директорию, если она не существует
                        }
                        val zipFile = File(uploadDir, part.originalFileName ?: "uploaded-archive.zip")
                        zipFile.writeBytes(fileBytes)
                        extractZip(zipFile, uploadDir)
                        part.dispose()
                    }
                }

                // Вызываем колбек, если он существует
                uploadLogsCallbacks[stId]?.complete(Unit)
                uploadLogsCallbacks.remove(stId)

                call.respondText("Archive uploaded and extracted successfully")
            }

        }


    }
}


