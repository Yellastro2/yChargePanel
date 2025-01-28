package com.yellastrodev

import com.yellastrodev.CommandsManager.waitForEventOrTimeout
import com.yellastrodev.databases.Station
import com.yellastrodev.databases.database
import com.yellastrodev.yLogger.AppLogger
import com.yellastrodev.ymtserial.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.json.JSONArray
import org.json.JSONObject



fun Application.configureStationRouting() {

    val TAG = "StationApi"

    val MAX_EVENTS_SIZE = 100




    fun createCommanJson(fComand: JSONObject): JSONObject {
        val fKey = fComand.keys().next()
        return JSONObject(mapOf(
            KEY_COMMAND to fKey,
            KEY_VALUE to fComand[fKey]
        ))
    }

    routing {
        authenticate("auth-apk-static") {
            route("/stationApi") {
                get("/$ROUT_CHECKIN") {
//                    val jedis = jedisPool.resource
                    try {
                        AppLogger.debug(TAG, "Checkin ${call.request.rawQueryParameters}")
                        val stId = call.request.queryParameters[KEY_STATION_ID]
                        val size = call.request.queryParameters[KEY_SIZE]?.toInt()
                        var state = call.request.queryParameters[KEY_STATE]
                        val timeoutHeader = call.request.headers[KEY_TIMEOUT]?.toIntOrNull()
                        val trafficLastDay = call.request.queryParameters[KEY_TRAFFIC]
                        val events = call.request.queryParameters[KEY_EVENT]

                        AppLogger.info(TAG, "timeout header: $timeoutHeader")

                        if (stId != null && timeoutHeader != null) {
                            val timestamp = (System.currentTimeMillis() / 1000).toInt()
//                            AppLogger.info(TAG, "Checkin $stId size: $size state: $state timeout: $timeoutHeader traffic: $trafficLastDay events: $events")

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
                                AppLogger.error(TAG, "Error on $stId", e)
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

                            trafficLastDay?.let {
                                fStation.lastDayTraffic = it
                                isUpdated = true
                            }

                            events?.let {
                                // тут чекаем как эвенты меняют стейт станции.
                                AppLogger.info(TAG,"find events: $events")

                                // упорядочиваем к самым новым
                                val fEventsSort = JSONArray(it).sortedBy {
                                    (it as JSONObject).getInt(KEY_DATE)
                                } as List<JSONObject>

                                // достаем стейт банков (список банков индексированый по слотам)
                                // либо он был передан в запросе со станции, либо берем из базы
                                val stateJSON = state?.let { JSONObject(it) } ?: run {
                                    fStation.state
                                }

                                // сохраняем строковый отпечаток стейта для дальше сравнения
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

                                if (stateStringPrevius != stateJSON.toString()) {
                                    state = stateJSON.toString()
                                    AppLogger.info(TAG,"NEW EVENT RECEIVED")
                                    isUpdated = true
                                }
                                //TODO
                            }

                            var onlineState: JSONObject = JSONObject()

                            state?.let {
                                if (fStation.state.toString() != it) {
                                    isUpdated = true
                                    fStation.state = JSONObject(it)
                                }
                                val fields = "{}" //jedis.hgetAll(key) TODO
                                val stateJSON = JSONObject(fields)
                                if (stateJSON.has(CMD_CHANGE_WALLPAPER))
                                    onlineState = JSONObject(
                                        mapOf(CMD_CHANGE_WALLPAPER to stateJSON.getString(CMD_CHANGE_WALLPAPER))
                                    )
                                if (stateJSON.has(CMD_CHANGE_QR))
                                    onlineState.put(CMD_CHANGE_QR, stateJSON.get(CMD_CHANGE_QR))
                            }

                            val exists = false
                            if (isUpdated)
                                try {
                                    database.updateStation(fStation)
                                } catch (e: Exception) {
                                    AppLogger.error(TAG, "Error on UPDATE $stId\n" +
                                            "on /stationApi/$ROUT_CHECKIN", e)
                                    call.response.status(HttpStatusCode.InternalServerError)
                                    return@get
                                }

                            val newEvent = waitForEventOrTimeout(stId, timeoutHeader)

                            val response = if (newEvent != null) {
                                createCommanJson(newEvent)
                            } else {
                                JSONObject(mapOf(KEY_COMMAND to "pong", "code" to 200))
                            }

                            if (onlineState.length() > 0) {
                                onlineState.remove(response.optString(KEY_COMMAND))
                                response.put(KEY_ONLINE_STATE, onlineState)
                            }


                            var resp = "Station added: ${stId}"
                            if (exists) {
                                resp = "Station updated: ${stId}"
                            }

                            AppLogger.info(TAG,"send responce to $stId: $response")

                            call.respond(HttpStatusCode.OK, response.toString())
                        } else {
                            AppLogger.warn(TAG,"Missing or invalid query parameters")
                            call.respond(HttpStatusCode.BadRequest, "Missing or invalid query parameters")
                        }

                    } catch (e: Exception) {
                        AppLogger.error(TAG, "An error occurred", e)
                    } finally {
//                        jedis.close()
                    }
                }
            }
        }
    }
}