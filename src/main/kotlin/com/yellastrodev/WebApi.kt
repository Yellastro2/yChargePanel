package com.yellastrodev

import com.yellastrodev.CommandsManager.isClientConnected
import com.yellastrodev.CommandsManager.sendCommandToStation
import com.yellastrodev.CommandsManager.setWallpaper
import com.yellastrodev.DatabaseManager.jedisPool
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
data class Station(val id: String, val name: String)

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
                val stId = call.request.queryParameters[KEY_STATION_ID]
                val num = call.request.queryParameters[KEY_NUM]
                if (stId != null && num != null) {

//                waitMap[stId]?.complete(JsonObject(mapOf("release" to JsonPrimitive(num))))
                    sendCommandToStation(stId, JSONObject(mapOf(CMD_RELEASE to num)))
                    call.respond(HttpStatusCode.OK, """{"status": "released"}""")
                } else {

                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing $KEY_STATION_ID or $KEY_NUM parameter")
                }
            }





            get("/$ROUT_DOWNLOAD") {
                val filePath = call.request.queryParameters[KEY_PATH]
                if (filePath != null) {
                    val file = File("$PATH_BASE/$filePath")
                    if (file.exists()) {
                        call.respondFile(file)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Файл не найден")
                    }
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Параметр 'path' отсутствует")
                }
            }

            post("/$ROUT_UPLOAD_WALLPAPER/{$KEY_STATION_ID}") {
                val stId = call.parameters[KEY_STATION_ID]
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
                val stId = call.parameters[KEY_STATION_ID]
                try {

                    val QRString = call.receiveParameters()[KEY_TEXT]

                    jedisPool.resource.use { jedis ->
                        val key = "Stations:${stId}"
                        val fields = jedis.hgetAll(key)
                        val oldQr = fields[CMD_CHANGE_QR]
                        if (oldQr != QRString) {
                            jedis.hset(key, CMD_CHANGE_QR, QRString)
                            sendCommandToStation(stId, JSONObject(mapOf(CMD_CHANGE_QR to QRString)))
                        }
                    }


                    call.respondRedirect("ROUT_STATION/${stId}")
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respondRedirect("$ROUT_STATION/${stId}")
                }
            }



            get("/$ROUT_GET_LOGS") {
                val stId = call.request.queryParameters[KEY_STATION_ID]
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
                val jedis = jedisPool.resource
                try {
                    val pattern = "Stations:*"

                    val keys = jedis.keys(pattern)

                    val jsonArray = JsonArray(keys.mapNotNull { key ->
                        if (jedis.type(key) != "hash") {
                            jedis.del(key) // Удаление ключа, если тип данных отличается
                            null
                        } else {
                            val fields = jedis.hgetAll(key)
                            val stId = fields[KEY_STATION_ID]
                            val size = fields[KEY_SIZE]?.toIntOrNull()
                            val available = fields[KEY_STATE]?.let { Json.parseToJsonElement(it).jsonObject.size } ?: null
                            val timestamp = fields[KEY_TIMESTAMP]?.toLongOrNull()
                            val trafficLastDay = fields[KEY_TRAFFIC_LAST_DAY]

                            if (stId != null && size != null && available != null && timestamp != null) {
                                JsonObject(
                                    mapOf(
                                        KEY_STATION_ID to JsonPrimitive(stId),
                                        KEY_SIZE to JsonPrimitive(size),
                                        KEY_AVAIBLE to JsonPrimitive(available),
                                        KEY_TIMESTAMP to JsonPrimitive(timestamp),
                                        KEY_TRAFFIC_LAST_DAY to JsonPrimitive(trafficLastDay)
                                    )
                                )
                            } else {
                                null
                            }
                        }
                    })

                    call.respond(HttpStatusCode.OK, jsonArray.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    jedis.close()
                }

            }

            get("/$ROUT_STATIONINFO") {
                val jedis = jedisPool.resource
                try {
                    val stId = call.parameters[KEY_STATION_ID]
                    val key = "Stations:${stId}"

//                val key = jedis.keys(pattern)

//                val jsonArray = JsonArray(keys.mapNotNull { key ->
                    if (jedis.type(key) != "hash") {
                        jedis.del(key) // Удаление ключа, если тип данных отличается
                        call.respond(HttpStatusCode.OK, "station ${stId} not found")
                    } else {
                        val fields = jedis.hgetAll(key)
                        val stId = fields[KEY_STATION_ID]
                        val size = fields[KEY_SIZE]?.toIntOrNull()
                        val state = fields[KEY_STATE]?.let { JSONObject(it) }
                        val timestamp = fields[KEY_TIMESTAMP]?.toLongOrNull()
                        val trafficLastDay = fields[KEY_TRAFFIC_LAST_DAY]
                        
                        if (stId != null && size != null && state != null && timestamp != null) {
                            val fStationJson = JSONObject(
                                mapOf(
                                    KEY_STATION_ID to stId,
                                    KEY_SIZE to size,
                                    KEY_STATE to state,
                                    KEY_TIMESTAMP to timestamp,
                                    KEY_TRAFFIC_LAST_DAY to trafficLastDay
                                )
                            )
                            fields[KEY_EVENT]?.let {
                                fStationJson.put(KEY_EVENT, JSONArray(if (it != "") it else "[]"))
                            }
                            call.respond(HttpStatusCode.OK, fStationJson.toString())
                        } else {
                            call.respond(HttpStatusCode.OK, "somethk wrong")
                        }
                    }

//                call.respond(HttpStatusCode.OK, jsonArray.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    jedis.close()
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


