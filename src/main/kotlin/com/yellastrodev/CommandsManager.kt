package com.yellastrodev

import com.yellastrodev.databases.database
import com.yellastrodev.ymtserial.*
import com.yellastrodev.ymtserial.ylogger.AppLogger
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.Writer
import java.util.concurrent.TimeoutException

object CommandsManager {

    val TAG = "CommandsManager"

    val DISCONNECT_TIMEOUT = 10 * 1000L

    val commandPoolMap = HashMap<String, ArrayList<JSONObject>>()
    val waitMap: ConcurrentHashMap<String, CompletableFuture<JSONObject?>> = ConcurrentHashMap()

    val activeClients = ConcurrentHashMap<String, Job>()
    val commandFlow = MutableSharedFlow<Pair<String, JSONObject>>(extraBufferCapacity = 100)



    /**
     * поменять значение обоев в базе данных + отправить команду станции о смене обоев.
     * если даже команда не дойдет (мб офлайн), станция всёравно заметит через обновление онайн стейта
     * что id обоев стали разными локально и в базе
     */
    suspend fun setWallpaper(stId: String, newFileName: String) {


        try {
            val fStation = database.getStationById(stId)!!
            if (fStation.wallpaper != newFileName) {
                fStation.wallpaper = newFileName
                database.updateStation(fStation)
                sendCommandToStation(stId,JSONObject(mapOf(CMD_CHANGE_WALLPAPER to newFileName)))
            }
        } catch (e: Exception) {
            AppLogger.error("Error setWallpaper: ${e.message}", e)
        }
    }

    suspend fun updateAPK(stId: String, newFileName: String) {
        try {
            sendCommandToStation(stId,JSONObject(mapOf(CMD_UPDATE_APK to newFileName)))
        } catch (e: Exception) {
            AppLogger.error("Error updateAPK: ${e.message}", e)
        }
    }

    suspend fun updateWebview(stId: String, newFileName: String) {
        try {
            sendCommandToStation(stId,JSONObject(mapOf(CMD_CHANGE_WEBVIEW to newFileName)))
        } catch (e: Exception) {
            AppLogger.error("Error update webview: ${e.message}", e)
        }
    }

    fun cleanLongPool(stId: String?) {
        waitMap.remove(stId)?.complete(JSONObject(mapOf(
            KEY_COMMAND to CMD_CANCEL
        )))
    }

    suspend fun waitForEventOrTimeout(stId: String, timeout: Int): JSONObject? {
        // проверяем в очереди команд наличие ожидающих - если есть, отправляем самую старую, из очереди удаляем.
        if (commandPoolMap.containsKey(stId) && commandPoolMap[stId]!!.size > 0)
            return commandPoolMap[stId]!!.removeAt(0)
        // создаем пустую фьючу с ожиданием как на таймауте лонгпула, помещаем ее в мапу по ключу айди станции
        // потом можно эту фьючу найти в этой мапе и завершить командой. или она выкинет таймаут по истечении.
        val future = waitMap.computeIfAbsent(stId) { CompletableFuture<JSONObject?>() }
        return try {
            withTimeoutOrNull((if (timeout > 10) timeout - 5 else 0).toLong() * 1000) {
                future.await()
            }
        } catch (e: TimeoutCancellationException) {
            AppLogger.info("TimeoutCancellationException ")
            null
        } catch (e: TimeoutException) {
            AppLogger.info("TimeoutException ")
            null
        } catch (e: CancellationException){
            AppLogger.info("CancellationException cuz longpool timeout future cancelled")
            return null
        }
        finally {
            waitMap.remove(stId)
        }
    }


    suspend fun sendCommandToStation(stId: String, command: JSONObject) {
        commandFlow.emit(stId to command)
    }

//    fun sendCommandToStation(stId: String, commandJSON: JSONObject) {
//        if (waitMap.containsKey(stId))
//            waitMap[stId]?.complete(commandJSON)
//        else {
//            commandPoolMap[stId] ?: kotlin.run {
//                commandPoolMap[stId] = java.util.ArrayList()
//            }
//            commandPoolMap[stId]!!.add(commandJSON)
//        }
//    }


    fun isClientConnected(stId: String): Boolean {
        return activeClients[stId]?.isActive == true
    }

    val stationTimers = ConcurrentHashMap<String, Job>()

    val mutex = Mutex()

    suspend fun runStationDisconnectTimer(stId: String) {
        // Получаем блокировку, чтобы предотвратить параллельный доступ
//        runBlocking {
            mutex.withLock {
                AppLogger.debug("runStationDisconnectTimer stationTimers[$stId]?.cancel() ")
                stationTimers[stId]?.cancel()
                AppLogger.debug("runStationDisconnectTimer run disconect timer ")
                stationTimers[stId] = CoroutineScope(Dispatchers.IO).launch {
                    AppLogger.debug("runStationDisconnectTimer launch ")
                    delay(DISCONNECT_TIMEOUT) // Ожидание 5 секунд
                    stationTimers.remove(stId)
                    AppLogger.warn("Станция $stId ожидание прошло: ${this.isActive}")
                    AppLogger.warn("Станция $stId потеряла соединение!")
                    val fEvent = JSONObject()

                    fEvent.put("date", System.currentTimeMillis())
                    fEvent.put(EVENT_TYPE, EVENT_CONNECTION)
                    fEvent.put(EVENT_CONNECTION, "disconnect")

                    addEventToStation(stId, fEvent)

                }
            }
//        }
    }

    suspend fun resetStationTimer(stId: String) {
        AppLogger.debug("resetStationTimer stationTimers[$stId]?.cancel() \n" +
                "${stationTimers.containsKey(stId)}")
        cleanLongPool(stId)
        mutex.withLock {
            AppLogger.debug("resetStationTimer withLock")

            stationTimers[stId]?.cancel() // Отменяем старый таймер, если есть
                ?: run {
                    AppLogger.warn("Станция $stId восстановила соединение!")
                    val fEvent = JSONObject()

                    fEvent.put("date", System.currentTimeMillis())
                    fEvent.put(EVENT_TYPE, EVENT_CONNECTION)
                    fEvent.put(EVENT_CONNECTION, "connect restored")

                    addEventToStation(stId, fEvent)
                }
            stationTimers.remove(stId)
        }
    }

}
