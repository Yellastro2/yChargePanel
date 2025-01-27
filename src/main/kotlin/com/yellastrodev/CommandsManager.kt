package com.yellastrodev

import com.yellastrodev.yLogger.AppLogger
import com.yellastrodev.ymtserial.CMD_CHANGE_WALLPAPER
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.await
import org.json.JSONObject
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException

object CommandsManager {

    val TAG = "CommandsManager"

    val waitMap: ConcurrentHashMap<String, CompletableFuture<JSONObject?>> = ConcurrentHashMap()

    fun setWallpaper(stId: String, newFileName: String) {
        // поменять значение обоев в базе данных + отправить команду станции о смене обоев.
        // если даже команда не дойдет (мб офлайн), станция всёравно замет что id обоев стали разными локально и в базе

        try {
            val fStation = database.getStationById(stId)!!
            if (fStation.wallpaper != newFileName) {
                fStation.wallpaper = newFileName
                database.updateStation(fStation)
                sendCommandToStation(stId,JSONObject(mapOf(CMD_CHANGE_WALLPAPER to newFileName)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun waitForEventOrTimeout(stId: String, timeout: Int): JSONObject? {
        // проверяем в очереди команд наличие ожидающих - если есть, отправляем самую старую, из очереди удаляем.
        if (commandPoolMap.containsKey(stId) && commandPoolMap[stId]!!.size > 0)
            return commandPoolMap[stId]!!.removeAt(0)

        // создаем пустую фьючу с ожиданием как на таймауте лонгпула, помещаем ее в мапу по ключу айди станции
        // потом можно эту фьючу найти в этой мапе и завершить командой. или она выкинет таймаут по истечении.
        val future = waitMap.computeIfAbsent(stId) { CompletableFuture<JSONObject?>() }
        return try {
            // если таймаут стоит меньше 10 секунд, значит это запрос на первое подключение и нужно сразу же ответить что серв на связи
            withTimeoutOrNull((if (timeout > 10) timeout - 5 else 0).toLong() * 1000) {
                future.await()
            }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: TimeoutException) {
            null
        } catch (e: CancellationException){
            AppLogger.info(TAG, "CancellationException cuz longpool timeout future cancelled")
            return null
        }
        finally {
            waitMap.remove(stId)
        }
    }

    val commandPoolMap = HashMap<String, ArrayList<JSONObject>>()

    fun sendCommandToStation(stId: String, commandJSON: JSONObject) {
        if (waitMap.containsKey(stId))
            waitMap[stId]?.complete(commandJSON)
        else {
            commandPoolMap[stId] ?: kotlin.run {
                commandPoolMap[stId] = java.util.ArrayList()
            }
            commandPoolMap[stId]!!.add(commandJSON)
        }
    }


    fun isClientConnected(stId: String): Boolean {
        return waitMap.containsKey(stId)
    }
}
