package com.yellastrodev

import com.yellastrodev.CommandsManager.activeClients
import com.yellastrodev.CommandsManager.cleanLongPool
import com.yellastrodev.CommandsManager.commandFlow
import com.yellastrodev.CommandsManager.resetStationTimer
import com.yellastrodev.CommandsManager.runStationDisconnectTimer
import com.yellastrodev.CommandsManager.waitForEventOrTimeout
import com.yellastrodev.CommandsManager.waitMap
import com.yellastrodev.databases.entities.Station
import com.yellastrodev.databases.database
import com.yellastrodev.ymtserial.*
import com.yellastrodev.ymtserial.ylogger.AppLogger
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.io.readByteArray
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.time.Duration

val MAX_EVENTS_SIZE = 100

/**
 * Добавляем эвент в станцию, связанный с бэкендом - онлай\офлайн итп.
 * не использовать для эвентов связанных с слотами\банками
 */
suspend fun addEventToStation(stId: String, eventJson: JSONObject) {
    val station = database.getStationById(stId) ?: return  // Если станция не найдена, возвращаем false
    var isUpdated = false

    BusinessClient.sendEventsToBusiness(stId, listOf(eventJson))

    // Добавляем эвент в список станции
    station.events.add(eventJson)
    while (station.events.size > MAX_EVENTS_SIZE)
        station.events.removeAt(0)

    database.updateStation(station)

}



fun Application.configureStationRouting() {

    val TAG = "StationApi"
    val routPath = "stationApi"

    fun createCommanJson(fComand: JSONObject): JSONObject {
        val fKey = fComand.keys().next()
        return JSONObject(mapOf(
            KEY_COMMAND to fKey,
            KEY_VALUE to fComand[fKey]
        ))
    }

    routing {


        route("/$routPath") {
            swaggerUI(path = "swagger", swaggerFile = "openapi/stationApi.yaml")
            }
        authenticate("auth-apk-static") {



            route("/$routPath") {
                webSocket("/ws") {
                    AppLogger.debug("WebSocket connection established")
                    val stId = call.request.queryParameters[KEY_STATION_ID] ?: run {
                        AppLogger.debug("WebSocket connection отмена, не указан $KEY_STATION_ID")
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing $KEY_STATION_ID"))
                        return@webSocket
                    }

                    val size = call.request.queryParameters[KEY_SIZE]?.toInt()
                    var state = call.request.queryParameters[KEY_STATE]?.let {
                        JSONObject(it)
                    }?: run {null}
                    val packageVersion = call.request.queryParameters[PACKAGE_VERSION]

                    ClientEventManager.onClientEvent(
                        stId,
                        size = size,
                        state = state,
                        versionName = packageVersion)

                    // Канал для таймера
                    val timeoutChannel = Channel<Unit>(capacity = 1)

                    // Пинг каждые 55 секунд
                    val pingJob = launch {
                        while (isActive) {
                            delay(55 * 1000L)
                            send(Frame.Ping("ping".toByteArray()))
                            timeoutChannel.trySend(Unit) // Отправляем сигнал для таймера
                        }
                    }

                    // Подписываемся на поток команд
                    val commandJob = launch {
                        commandFlow.collect { (targetId, command) ->
                            if (targetId == stId) {
                                val fCommand = createCommanJson(command)
                                send(Frame.Text(fCommand.toString()))
                            }
                        }
                    }.also { activeClients[stId] = it }

                    try {
                        for (frame in incoming) {
                            AppLogger.info("WebSocket Получен фрейм $stId")
                            when (frame) {
                                is Frame.Text -> {
                                    val receivedText = frame.readText()
                                    val json = JSONObject(receivedText)

                                    ClientEventManager.onClientEvent(
                                        stId,
                                        json.optJSONObject(KEY_STATE, null),
                                        if (json.has(KEY_SIZE)) json.getInt(KEY_SIZE) else null,
                                        json.optString(PACKAGE_VERSION, null),
                                        json.optString(KEY_TRAFFIC_LAST_DAY, null),
                                        json.optJSONObject(KEY_EVENT, null)
                                    )
                                    timeoutChannel.trySend(Unit) // Сбрасываем таймер, если пришёл pong
                                }
                                is Frame.Ping ->  send(Frame.Pong(ByteArray(0)))
                                is Frame.Pong -> send(Frame.Ping(ByteArray(0)))
                                else -> AppLogger.error("WebSocket Получен неподдерживаемый тип фрейма $stId")
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.error("WebSocket error for $stId", e)
                    } finally {
                        pingJob.cancel()
                        commandJob.cancel()
                        activeClients[stId]?.cancel()
                        activeClients.remove(stId)
                    }
                }

                get("/$ROUT_CHECKIN") {
                    var stId: String? = null
                    try {

                        AppLogger.debug("Checkin ${call.request.rawQueryParameters}")
                        stId = call.request.queryParameters[KEY_STATION_ID]
                        if (waitMap.containsKey(stId)) {
                            AppLogger.debug( "get/$ROUT_CHECKIN drop existing $stId future")
                            cleanLongPool(stId)
                        }
                        val size = call.request.queryParameters[KEY_SIZE]?.toInt()
                        var state = call.request.queryParameters[KEY_STATE]
                        val timeoutHeader = call.request.headers[KEY_TIMEOUT]?.toIntOrNull()
                        val trafficLastDay = call.request.queryParameters[KEY_TRAFFIC]
                        val events = call.request.queryParameters[KEY_EVENT]
                        val versionName = call.request.queryParameters[PACKAGE_VERSION]

                        AppLogger.info("timeout header: $timeoutHeader")

                        if (stId != null && timeoutHeader != null) {
                            resetStationTimer(stId)
                            val timestamp = (System.currentTimeMillis() / 1000).toInt()
//                            AppLogger.info("Checkin $stId size: $size state: $state timeout: $timeoutHeader traffic: $trafficLastDay events: $events")

                            var isUpdated = false
                            val fStation = try {
                                 database.getStationById(stId) ?: run {
                                    isUpdated = true
                                    Station(
                                        stId,
                                        timestamp = timestamp
                                    )
                                }
                            } catch (e: Exception) {
                                AppLogger.error("Error on $stId", e)
                                call.response.status(HttpStatusCode.InternalServerError)
                                return@get
                            }

                            if (timestamp != fStation.timestamp) {
                                isUpdated = true
                                fStation.timestamp = timestamp
                            }

                            size?.let {
                                if (fStation.size != it) {
                                    isUpdated = true
                                    fStation.size = it
                                }
                            }

                            versionName?.let {
                                if (fStation.apkVersion != it) {
                                    isUpdated = true
                                    fStation.apkVersion = it
                                }
                            }

                            trafficLastDay?.let {
                                fStation.lastDayTraffic = it
                                isUpdated = true
                            }



                            events?.let {
                                // тут чекаем как эвенты меняют стейт станции.
                                AppLogger.info("find events: $events")

                                // упорядочиваем к самым новым
                                val fEventsSort = JSONArray(it).sortedBy {
                                    (it as JSONObject).getInt(KEY_DATE)
                                } as List<JSONObject>

                                // достаем стейт банков (список банков индексированый по слотам)
                                // либо он был передан в запросе со станции, либо берем из базы
                                val stateJSON = state?.let { JSONObject(it) } ?: run {
                                    fStation.state
                                }

                                // сохраняем строковый отпечаток стейта для дальнейшего сравнения
                                val stateStringPrevius = stateJSON.toString()

                                // каждый эвент: 1. сохраняем в список всех последних эвентов
                                fEventsSort.forEach { qEvent ->
//                                    fPreviusEvents.put(qEvent)

                                    fStation.events.add(qEvent)
                                    // слот айди значит эвент касается банка, будет влиять на стейт, разбираемся чо за эвент
                                    if (qEvent.has(EVENT_SLOT_ID)) {
                                        val qSlot = qEvent.getString(EVENT_SLOT_ID)
                                        val qEventType = qEvent.getString(EVENT_TYPE)

                                        if (stateJSON.has(qSlot)) {
                                            if (qEventType == EVENT_REMOVE_BANK)
                                                stateJSON.remove(qSlot)
                                            else {
                                                stateJSON.put(qSlot, qEvent)
                                            }
                                        } else {
                                            if (qEventType == EVENT_ADD_BANK)
                                                stateJSON.put(qSlot, qEvent)
                                        }
                                    }
                                }

                                while (fStation.events.size > MAX_EVENTS_SIZE)
                                    fStation.events.removeAt(0)

//                                value[KEY_EVENT] = JSONArray(fPreviusEvents).toString()

                                // если стейт слотов в базе не совпадает с новым, то обновляем
                                if (fStation.state.toString() != stateJSON.toString()) {
                                    fStation.state = stateJSON
                                    AppLogger.info("найдены эвенты обновления стейта слотов!")
                                    isUpdated = true
                                }

                                BusinessClient.sendEventsToBusiness(stId, fEventsSort)
                            }

                            // доп. состояния будут отправлены на станцию если она налаживает соединение после офлайна,
                            // то есть когда timeoutHeader = 10. это состояния актуальных обоев и кюара, вдруг команды
                            // их смены не дошли таки до станции.
                            var onlineState: JSONObject = JSONObject()

                            if (timeoutHeader <= 10){
                                val fWallpaper = if (fStation.wallpaper.isNotEmpty()) fStation.wallpaper
                                else DEFAULT_IMAGE

//                                if (stateJSON.has(CMD_CHANGE_WALLPAPER))
                                onlineState = JSONObject(
                                    mapOf(CMD_CHANGE_WALLPAPER to fWallpaper)
                                )
                                if (fStation.qrString.isNotEmpty())
                                    onlineState.put(CMD_CHANGE_QR, fStation.qrString)
                            }



                            val exists = false
                            if (isUpdated)
                                try {
                                    database.updateStation(fStation)
                                } catch (e: Exception) {
                                    AppLogger.error("Error on UPDATE $stId\n" +
                                            "on /stationApi/$ROUT_CHECKIN", e)
                                    call.response.status(HttpStatusCode.InternalServerError)
                                    return@get
                                }

                            // там запускается фьюча с таймаутом - 5 которая ждет команды для станции либо возрвращает null по таймауту
                            val newEvent = waitForEventOrTimeout(stId, timeoutHeader)


                            val response = if (newEvent != null) {
                                if (newEvent.optString(KEY_COMMAND) == CMD_CANCEL)
                                    return@get
                                createCommanJson(newEvent)
                            } else {
                                JSONObject(mapOf(KEY_COMMAND to "pong", "code" to 200))
                            }

                            if (onlineState.length() > 0) {
                                onlineState.remove(response.optString(KEY_COMMAND))
                                response.put(KEY_ONLINE_STATE, onlineState)
                            }

                            AppLogger.info("send responce to $stId: $response")
                            call.respond(HttpStatusCode.OK, response.toString())


                        } else {
                            AppLogger.warn("Missing or invalid query parameters")
                            call.respond(HttpStatusCode.BadRequest, "Missing or invalid query parameters")
                        }

                    } catch (e: Exception) {
                        AppLogger.error("An error occurred", e)
                    } finally {
                        stId?.let { runStationDisconnectTimer(stId) }
                    }
                }

                post("/$ROUT_UPLOADLOGS") {
                    AppLogger.info("$routPath/$ROUT_UPLOADLOGS")
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

                get("/$ROUT_DOWNLOAD") {
                    AppLogger.info("$routPath/$ROUT_DOWNLOAD")
                    try {
                        val params = extractParametersOrFail(call, listOf(KEY_PATH)) { errorMessage ->
                            call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                        } ?: return@get
                        val filePath = params[KEY_PATH]!!
                        AppLogger.info("download path: $filePath")
                        val file = File(filePath)
                        if (file.exists()) {
                            call.respondFile(file)
                        } else {
                            call.respond(HttpStatusCode.NotFound, "Файл не найден")
                        }
                    } catch (e: Exception) {
                        AppLogger.error("$routPath/$ROUT_DOWNLOAD", e)
                        call.respond(HttpStatusCode.InternalServerError, "Внутренняя ошибка сервера: \n${e.message}")
                    }

                }

                get("/delayed-response") {
//
//                    // Ваш код задержки
                    delay(30000) // 30 секунд
                    call.respond(HttpStatusCode.OK, "Ответ после паузы 30 секунд")

                }
            }
        }
    }
}
