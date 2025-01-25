package com.yellastrodev

import org.json.JSONObject

val database: DbManager = PostgreeManager()

// Определение класса Station
data class Station(
    val stId: String,
    var size: Int = 0,
    var lastDayTraffic: String = "",
    var state: JSONObject = JSONObject(), // Используем JSONObject для хранения состояния
    val events: ArrayList<JSONObject> = ArrayList(), // Список событий
    val timestamp: Int = 0,
    var qrString: String = "", // Новое поле для qrString
    var wallpaper: String = "" // Новое поле для wallpaper
)

interface DbManager {

    fun updateStation(station: Station)

    fun getStationById(stId: String): Station?

    fun getStations(limit: Int = 20, offset: Int = 0): List<Station>


}