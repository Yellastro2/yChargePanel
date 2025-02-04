package com.yellastrodev

import com.yellastrodev.ymtserial.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.json.JSONArray

object BusinessClient {
    private val eventQueue = ConcurrentLinkedQueue<JSONObject>()
    private val mutex = Mutex()
    private val client = HttpClient(CIO)
    private const val BUSINESS_SERVER_URL = "http://127.0.0.1:5001/events"

    init {
        // Запуск фоновой задачи для периодической отправки событий
        CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(1000) // Интервал в 1 секунду
                sendQueuedEvents()
            }
        }
    }

    suspend fun sendEventsToBusiness(stId: String, fEventsSort: List<JSONObject>) {
        val fFiltredList = fEventsSort.filter { qEvent ->
            val qType = qEvent.getString(EVENT_TYPE)
            when (qType) {
                EVENT_ADD_BANK -> true
                EVENT_REMOVE_BANK -> true
                EVENT_CHARGE -> {
                    qEvent.optInt(EVENT_CHARGE_NEW) >= 90 && qEvent.optInt(EVENT_CHARGE_OLD) <= 90
                }
                else -> false
            }
        }
        mutex.withLock {
            fFiltredList.forEach { event ->
                event.put("stationId", stId)
                eventQueue.add(event)
            }
        }
    }

    private suspend fun sendQueuedEvents() {
        val eventsToSend = mutableListOf<JSONObject>()

        mutex.withLock {
            while (eventQueue.isNotEmpty()) {
                eventsToSend.add(eventQueue.poll())
            }
        }

        if (eventsToSend.isNotEmpty()) {
            try {
                client.post(BUSINESS_SERVER_URL) {
                    contentType(ContentType.Application.Json)
                    setBody(JSONArray(eventsToSend).toString())
                }
                println("✅ Отправлено ${eventsToSend.size} событий")
            } catch (e: Exception) {
                println("❌ Ошибка при отправке событий: ${e.message}")
                // Возвращаем события обратно в очередь при ошибке
                mutex.withLock {
                    eventQueue.addAll(eventsToSend)
                }
            }
        }
    }
}
