package com.yellastrodev

import com.yellastrodev.CommandsManager.isClientConnected
import com.yellastrodev.CommandsManager.sendCommandToStation
import com.yellastrodev.CommandsManager.setWallpaper
import com.yellastrodev.CommandsManager.updateAPK
import com.yellastrodev.databases.entities.Station
import com.yellastrodev.databases.database
import com.yellastrodev.databases.entities.Powerbank
import com.yellastrodev.yLogger.AppLogger
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
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlinx.serialization.json.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.concurrent.CompletableFuture


fun Application.configureWebApiRouting() {

    // Карта для хранения колбеков
    val uploadLogsCallbacks = mutableMapOf<String, CompletableFuture<Unit>>()

    val TAG = "WebApiRouting"

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    routing {
        route("/api") {

            post("/$ROUT_UPLOAD_FILE_FORSTATION/{fileType}/{$KEY_STATION_ID}") {
                val params = extractParametersOrFail(call, listOf(KEY_STATION_ID, "fileType")) { errorMessage ->
                    call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                } ?: return@post

                val stId = params[KEY_STATION_ID]!!
                val fileType = params["fileType"]!!

                // Определение папки для сохранения файла в зависимости от fileType
                val (uploadDir, fFileType) = when (fileType) {
                    WALLPAPER_ROUT -> File(PATH_WALLPAPERS) to "jpg"  // для обоев
                    APK_ROUT -> File(PATH_APK) to "apk"               // для APK
                    else -> {
                        call.respondText("Invalid file type", status = HttpStatusCode.BadRequest)
                        return@post
                    }
                }

                if (!uploadDir.exists()) {
                    uploadDir.mkdirs()
                }

                // Найти максимальный номер файла
                val maxFileNumber = uploadDir.listFiles()?.mapNotNull {
                    it.name.substringBeforeLast(".").toIntOrNull()
                }?.maxOrNull() ?: 0
                val newFileName = "${maxFileNumber + 1}.$fFileType" // для wallpaper, для apk можно изменить расширение

                val multipart = call.receiveMultipart()
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val fileBytes = part.provider.invoke().readRemaining().readByteArray()
                        File(uploadDir, newFileName).writeBytes(fileBytes)
                    }
                    part.dispose()
                }

                // Вызов нужного метода в зависимости от fileType
                if (fileType == WALLPAPER_ROUT) {
                    setWallpaper(stId, newFileName)
                } else if (fileType == APK_ROUT) {
                    updateAPK(stId, newFileName)
                }

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
                    AppLogger.error(TAG, "An error occurred", e)
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
                // TODO_1
                // Проверяем, на связи ли клиент
                val isClientConnected = isClientConnected(stId)
                if (isClientConnected) {
                    // Создаем CompletableFuture для загрузки логов
                    val uploadFuture = CompletableFuture<Unit>()
                    uploadLogsCallbacks[stId] = uploadFuture

                    // Отправляем команду на получение логов
                    sendCommandToStation(stId, JSONObject(mapOf(CMD_GETLOGS to lastLogFile)))

                    // Ожидаем загрузки логов
                    val uploadCompleted = withTimeoutOrNull(TIMEOUT_LOG_UPLOAD * 1000) {
                        uploadLogsCallbacks[stId]?.await()
                    }
                    if (uploadCompleted != null) {
                        AppLogger.info(TAG,"""{"status": "logs uploaded"}""")
//                        call.respond(HttpStatusCode.OK, """{"status": "logs uploaded"}""")
                    } else {
                        AppLogger.info(TAG,"""{"status": "upload timeout"}""")
//                        call.respond(HttpStatusCode.RequestTimeout, """{"status": "upload timeout"}""")
                    }
                } else {
                    // Клиент не на связи, сразу отвечаем
                    AppLogger.info(TAG,"""{"status": "client not connected"}""")
//                    call.respond(HttpStatusCode.OK, """{"status": "client not connected"}""")
                }
                val zipFile = getLastLogZip(stId)
                call.respondFile(zipFile)
            }

            get("/$ROUT_STATIONLIST") {
                try {
                    // Получаем параметры `page` и `pageSize` из запроса
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
                    val filter = call.request.queryParameters["filter"]?: "" // Онлайн или офлайн фильтр

                    // Рассчитываем offset
                    val offset = (page - 1) * pageSize

                    val onlineTimestamp = ((System.currentTimeMillis() / 1000) - ONLINE_SECONDS).toInt()

                    // Получаем станции с учётом лимита и смещения
                    val fStationsInfo = database.getStations(limit = pageSize, offset = offset, filter = filter, onlineTimestamp)

                    val fStations = fStationsInfo.first

                    // Получаем общее количество станций для пагинации
                    val totalStations = database.getStationCount(onlineTimestamp)

                    // Формируем ответ с данными станций и общим количеством
                    val responseJson = JSONObject().apply {
                        put("stations", JSONArray().apply {
                            fStations.forEach { station ->
                                put(JSONObject().apply {
                                    put(KEY_STATION_ID, station.stId)
                                    put(KEY_SIZE, station.size)
                                    put(KEY_AVAIBLE, station.state.length()) // Количество полей в state
                                    put(KEY_TIMESTAMP, station.timestamp)
                                    put(KEY_TRAFFIC_LAST_DAY, station.lastDayTraffic)
                                    put("wallpaper", if (station.wallpaper.isBlank()) DEFAULT_IMAGE else station.wallpaper)
                                    put("QRCode", station.qrString)

                                })
                            }
                        })
                        put("total", totalStations.first)
                        put("online", totalStations.second)
                        put("filtred",fStationsInfo.second)
                    }

                    call.respond(HttpStatusCode.OK, responseJson.toString())
                } catch (e: Exception) {
                    AppLogger.error(TAG, "An error occurred", e)
                    call.respond(HttpStatusCode.InternalServerError, "An error occurred: ${e.printStackTrace()}",)
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

                        // Обновление state с информацией о блокировке банков
                        val stateWithBlockedInfo = JSONObject(fStation.state.toString()) // Создаём копию state
                        val bankIds = fStation.state.keys().asSequence()
                            .mapNotNull { key -> fStation.state.getJSONObject(key).optString("bankId", null) }
                            .toList()

                        val powerbanks = database.getPowerbanksByIds(bankIds) // Получаем статусы банков из базы
                        val blockedBankIds = powerbanks.filter { it.status == Station.Status.BLOCKED }.map { it.id }.toSet()

                        stateWithBlockedInfo.keys().forEach { slot ->
                            val bank = stateWithBlockedInfo.getJSONObject(slot)
                            val bankId = bank.optString(KEY_BANK_ID, null)
                            if (bankId != null && blockedBankIds.contains(bankId)) {
                                bank.put("blocked", true) // Добавляем ключ "blocked: true", если банк заблокирован
                            }
                        }

                        put(KEY_STATE, stateWithBlockedInfo)

                        put(KEY_TIMESTAMP, fStation.timestamp)
                        put(KEY_TRAFFIC_LAST_DAY, fStation.lastDayTraffic)
                        // Проверяем наличие событий и добавляем их в JSON
                        if (fStation.events.isNotEmpty()) {
                            put(KEY_EVENT, JSONArray(fStation.events))
                        } else {
                            put(KEY_EVENT, JSONArray())  // Если событий нет, добавляем пустой массив
                        }
                        val blockedSlotsState = fStation.blockedSlots.map { if (it == Station.Status.BLOCKED) 1 else 0 }

                        put("blockedSlots", JSONArray(blockedSlotsState))


                    }
                    call.respond(HttpStatusCode.OK, fStationJson.toString())
                } catch (e: Exception) {
                    AppLogger.error(TAG, "An error occurred", e)
                }
            }

            get("/$ROUT_UPDATE_BANK_STATUS") {
                try {
                    // Получаем параметр bankId из запроса
                    val params = extractParametersOrFail(call, listOf(KEY_BANK_ID)) { errorMessage ->
                        call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                    } ?: return@get

                    val bankId = params[KEY_BANK_ID]!!

                    // Получаем текущий статус банка из базы данных
                    val currentBank = database.getPowerbankById(bankId)
                        ?: Powerbank(bankId, Station.Status.AVAILABLE)
                    // Меняем статус на противоположный
                    currentBank.status = if (currentBank.status == Station.Status.BLOCKED) {
                        Station.Status.AVAILABLE
                    } else {
                        Station.Status.BLOCKED
                    }

                    // Обновляем статус банка в базе данных
                    database.updatePowerbank(currentBank)

                    call.respond(HttpStatusCode.OK, """{"status": "Bank status updated to ${currentBank.status.name}."}""")
                } catch (e: Exception) {
                    AppLogger.error(TAG, "An error occurred while updating bank status", e)
                    call.respondText("An error occurred while processing your request.", status = HttpStatusCode.InternalServerError)
                }
            }


            get("/$ROUT_RELEASE") {
                val params = extractParametersOrFail(call, listOf(KEY_STATION_ID, KEY_NUM)) { errorMessage ->
                    call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                } ?: return@get

                val stId = params[KEY_STATION_ID]!!
                val num = params[KEY_NUM]!!.toInt()

                database.getStationById(stId)
                    ?.let { fStation ->
                        if (fStation.blockedSlots[num - 1] == Station.Status.BLOCKED) {
                            call.respond(HttpStatusCode.OK, """{"status": "slot is blocked"}""")
                            return@get
                        }
                        fStation.state.optJSONObject(num.toString(), null)
                        ?. optString(KEY_BANK_ID) ?. let { fBankId ->
                        database.getPowerbankById(fBankId)?.let {
                            if (it.status == Station.Status.BLOCKED) {
                                call.respond(HttpStatusCode.OK, """{"status": "powerbank is blocked"}""")
                                return@get
                            }
                        }
                    }
                }



                sendCommandToStation(stId, JSONObject(mapOf(CMD_RELEASE to num)))
                call.respond(HttpStatusCode.OK, """{"status": "command released send"}""")
            }

            get("/$ROUT_FORCE") {
                val params = extractParametersOrFail(call, listOf(KEY_STATION_ID, KEY_NUM)) { errorMessage ->
                    call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                } ?: return@get

                val stId = params[KEY_STATION_ID]!!
                val num = params[KEY_NUM]!!

                sendCommandToStation(stId, JSONObject(mapOf(CMD_FORCE to num)))
                call.respond(HttpStatusCode.OK, """{"status": "command force released send"}""")
            }

            get("/$ROUT_REBOOT") {
                val params = extractParametersOrFail(call, listOf(KEY_STATION_ID)) { errorMessage ->
                    call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                } ?: return@get

                val stId = params[KEY_STATION_ID]!!

                sendCommandToStation(stId, JSONObject(mapOf(CMD_REBOOT to "1")))
                call.respond(HttpStatusCode.OK, """{"status": "command reboot send"}""")
            }

            get("/$ROUT_BLOCK_SLOT") {
                val params = extractParametersOrFail(call, listOf(KEY_STATION_ID, KEY_NUM)) { errorMessage ->
                    call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                } ?: return@get

                val stId = params[KEY_STATION_ID]!!
                val num = params[KEY_NUM]!!.toInt() - 1

                val fStation = database.getStationById(stId)

                fStation?.let {
                    fStation.blockedSlots[num] = fStation.blockedSlots[num].let {
                        if (it == Station.Status.BLOCKED) Station.Status.AVAILABLE else Station.Status.BLOCKED
                    }
                    database.updateStation(fStation)
                    val fResult = fStation.blockedSlots[num].toString()
                    call.respond(HttpStatusCode.OK, """{"status": "$fResult"}""")
                } ?: run {
                    call.respond(HttpStatusCode.NotFound, """{"status": "station not found"}""")
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


