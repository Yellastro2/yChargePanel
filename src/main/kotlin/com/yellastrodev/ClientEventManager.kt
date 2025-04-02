package com.yellastrodev

import com.yellastrodev.CommandsManager.waitForEventOrTimeout
import com.yellastrodev.databases.database
import com.yellastrodev.databases.entities.Station
import com.yellastrodev.ymtserial.*
import com.yellastrodev.ymtserial.ylogger.AppLogger
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class ClientEventManager {

    companion object {

        private val mutexMap = ConcurrentHashMap<String, Mutex>()

        suspend fun onClientEvent(stId: String,
                                  state: JSONObject? = null,
                                  size: Int? = null,
                                  versionName: String? = null,
                                  trafficLastDay: String? = null,
                                  events: JSONObject? = null): HttpStatusCode {
            val timestamp = (System.currentTimeMillis() / 1000).toInt()

            val mutex = mutexMap.getOrPut(stId) { Mutex() }

            return mutex.withLock {
                try {
                    AppLogger.debug("мутексная обработка сообщения от станции $stId")
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
                        return HttpStatusCode.InternalServerError
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

                    val stateJSON = state ?: run {
                        JSONObject(fStation.state.toString())
                    }

                    events?.let {
                        // тут чекаем как эвенты меняют стейт станции.
                        AppLogger.info("find events: $events")

                        // упорядочиваем к самым новым
                        val fEventsSort = try {
                            JSONArray(it).sortedBy {
                                (it as JSONObject).getInt(KEY_DATE)
                            } as List<JSONObject>
                        } catch (e: Exception) {
                            listOf(it)
                        }

                        // достаем стейт банков (список банков индексированый по слотам)
                        // либо он был передан в запросе со станции, либо берем из базы


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


                        BusinessClient.sendEventsToBusiness(stId, fEventsSort)
                    }

                    if (fStation.state.toString() != stateJSON.toString()) {
                        fStation.state = stateJSON
                        AppLogger.info("найдены обновления стейта слотов!")
                        isUpdated = true
                    }

                    if (isUpdated)
                        try {
                            database.updateStation(fStation)
                        } catch (e: Exception) {
                            AppLogger.error(
                                "Error on UPDATE $stId\n" +
                                        "on /stationApi/$ROUT_CHECKIN", e
                            )
                            return HttpStatusCode.InternalServerError
                        }

                    return HttpStatusCode.OK
                } finally {
                    AppLogger.debug("мутексная обработка $stId завершена")
                    mutexMap.remove(stId, mutex) // Чистим `Mutex`, если он больше не нужен
                }
            }
        }
    }
}