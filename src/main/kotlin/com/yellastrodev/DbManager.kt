package com.yellastrodev

import com.yellastrodev.databases.PostgreeManager
import org.json.JSONObject

val database: DbManager = PostgreeManager()

// Определение класса Station
data class Station(
    val stId: String,
    var size: Int = 0,
    var lastDayTraffic: String = "",
    var state: JSONObject = JSONObject(), // Используем JSONObject для хранения состояния
    val events: ArrayList<JSONObject> = ArrayList(), // Список событий
    var timestamp: Int = 0,
    var qrString: String = "", // Новое поле для qrString
    var wallpaper: String = "" // Новое поле для wallpaper
)

interface DbManager {

    fun updateStation(station: Station)

    fun getStationById(stId: String): Station?

    /**
     * возвращает пару (список станций - одна страница , количество станций прошедших фильтр - без учета страницы)
     */
    fun getStations(limit: Int = 20, offset: Int = 0, filter: String = "", onlineSeconds: Int = 0): Pair<List<Station>, Int>

    /**
     * возвращает (количество станций в базе , станции с timestamp > fTimestamp) - общее и онлайн
     */
    fun getStationCount(fTimestamp: Int = 0): Pair<Int, Int>


}