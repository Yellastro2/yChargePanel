package com.yellastrodev

import com.yellastrodev.CommandsManager.waitForEventOrTimeout
import com.yellastrodev.DatabaseManager.jedisPool
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
        authenticate("auth-jwt") {
            route("/stationApi") {
                get("/$ROUT_CHECKIN") {
                    val jedis = jedisPool.resource
                    try {
                        val stId = call.request.queryParameters[KEY_STATION_ID]
                        val size = call.request.queryParameters[KEY_SIZE]?.toIntOrNull()
                        var state = call.request.queryParameters[KEY_STATE]
                        val timeoutHeader = call.request.headers[KEY_TIMEOUT]?.toIntOrNull()
                        val trafficLastDay = call.request.queryParameters[KEY_TRAFFIC]
                        val events = call.request.queryParameters[KEY_EVENT]
                        print(trafficLastDay)

                        if (stId != null && timeoutHeader != null) {
                            val timestamp = System.currentTimeMillis() / 1000

                            AppLogger.info(TAG, "Checkin $stId size: $size state: $state timeout: $timeoutHeader traffic: $trafficLastDay events: $events")

                            val key = "Stations:${stId}"
                            val value = hashMapOf(
                                KEY_STATION_ID to stId,
                                KEY_TIMESTAMP to timestamp.toString()
                            )
                            size?.let { value[KEY_SIZE] = it.toString() }

                            trafficLastDay?.let { value[KEY_TRAFFIC_LAST_DAY] = it }

                            events?.let {
                                // тут чекаем как эвенты меняют стейт станции.

                                // упорядочиваем к самым новым
                                val fEventsSort = JSONArray(it).sortedBy {
                                    (it as JSONObject).getInt(KEY_DATE)
                                } as List<JSONObject>

                                // достаем сохраненный в базе список эвентов, что бы добавить в него новые.
                                val fields = jedis.hgetAll(key)
                                var fSavedEventsStr = fields[KEY_EVENT] ?: "[]"
                                if (fSavedEventsStr == "")
                                    fSavedEventsStr = "[]"
                                val fPreviusEvents = JSONArray(fSavedEventsStr)

                                // достаем стейт банков (список банков индексированый по слотам)
                                // либо он был передан в запросе со станции, либо берем из базы
                                val stateJSON = state?.let { JSONObject(it) } ?: run {
                                    if (jedis.type(key) != "hash") {
                                        jedis.del(key)
                                        return@run JSONObject()
                                    }
                                    fields[KEY_STATE]?.let { itStateFromRedis ->
                                        JSONObject(itStateFromRedis)
                                    } ?: JSONObject()
                                }

                                // сохраняем строковый отпечаток стейта для дальше сравнения
                                val stateStringPrevius = stateJSON.toString()

                                // каждый эвент: 1. сохраняем в список всех последних эвентов
                                fEventsSort.forEach { qEvent ->
                                    fPreviusEvents.put(qEvent)
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

                                while (fPreviusEvents.length() > MAX_EVENTS_SIZE)
                                    fPreviusEvents.remove(0)

                                value[KEY_EVENT] = JSONArray(fPreviusEvents).toString()

                                if (stateStringPrevius != stateJSON.toString()) {
                                    state = stateJSON.toString()
                                    print("NEW EVENT RECEIVED")
                                }
                                //TODO
                            }

                            var onlineState: JSONObject = JSONObject()

                            state?.let {
                                value[KEY_STATE] = it
                                val fields = jedis.hgetAll(key)
                                val stateJSON = JSONObject(fields)
                                if (stateJSON.has(CMD_CHANGE_WALLPAPER))
                                    onlineState = JSONObject(
                                        mapOf(CMD_CHANGE_WALLPAPER to stateJSON.getString(CMD_CHANGE_WALLPAPER))
                                    )
                                if (stateJSON.has(CMD_CHANGE_QR))
                                    onlineState.put(CMD_CHANGE_QR, stateJSON.get(CMD_CHANGE_QR))
                            }


                            // Проверка типа данных ключа
                            if (jedis.type(key) != "hash") {
                                jedis.del(key) // Удаление ключа, если тип данных отличается
                            }

                            val exists = jedis.exists(key)
                            jedis.hmset(key, value)

                            val newEvent = waitForEventOrTimeout(stId, timeoutHeader)

                            val response = if (newEvent != null) {
                                createCommanJson(newEvent)
//                        JsonObject(mapOf(KEY_COMMAND to JsonPrimitive("pong"), "code" to JsonPrimitive(200)))
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

                            call.respond(HttpStatusCode.OK, response.toString())
                        } else {
                            AppLogger.warn(TAG,"Missing or invalid query parameters")
                            call.respond(HttpStatusCode.BadRequest, "Missing or invalid query parameters")
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        jedis.close()
                    }
                }
            }
        }
    }
}