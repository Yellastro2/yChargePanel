package com.yellastrodev

import com.yellastrodev.yLogger.AppLogger
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Table.Dual.columns
import org.jetbrains.exposed.sql.transactions.transaction
import org.json.JSONArray
import org.json.JSONObject // Импортируем JSONObject, если используете библиотеку org.json

// Определение таблицы Stations
object Stations : Table() {
    val stId = varchar("stId", 255) // Поле для stId
    val size = integer("size") // Поле для size (целое число)
    val lastDayTraffic = text("lastDayTraffic") // Поле для lastDayTraffic (строка)
    val state = text("state") // Поле для state (JSON строка)
    val events = text("events") // Поле для events (JSON строка)
    val timestamp = integer("timestamp") // Поле для timestamp
    val qrString = text("qrString",).default("") // Новое поле для qrString
    val wallpaper = text("wallpaper").default("") // Новое поле для wallpaper
}





class PostgreeManager: DbManager {
    private val TAG = "PostgreeManager"

    private val url3 = "jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:6543/postgres"

    private val password = "7821493402Ss"

    init {
        Database.connect(
            url3,
            driver = "org.postgresql.Driver",
            user = "postgres.wqfqhszmsmsyvhjsgjuz",
            password = password
        )

        transaction {
            // Создание таблицы, если она не существует
            SchemaUtils.create(Stations)
        }
    }

    override fun updateStation(station: Station) {
        transaction {
            // Проверяем, существует ли станция с таким stId
            val existingStation = Stations.select(Stations.stId).where { Stations.stId eq station.stId }.singleOrNull()

            if (existingStation != null) {
                // Если станция существует, обновляем ее
                Stations.update({ Stations.stId eq station.stId }) {
                    it[size] = station.size // Обновляем поле size
                    it[lastDayTraffic] = station.lastDayTraffic // Обновляем поле lastDayTraffic
                    it[state] = station.state.toString() // Обновляем поле state
                    it[events] = arrayToString(station.events) // Обновляем поле events
                    it[timestamp] = station.timestamp // Обновляем поле timestamp
                    it[qrString] = station.qrString // Обновляем поле qrString
                    it[wallpaper] = station.wallpaper // Обновляем поле wallpaper
                }
            } else {
                // Если станции нет, вставляем новую
                Stations.insert {
                    it[stId] = station.stId
                    it[size] = station.size // Вставляем поле size
                    it[lastDayTraffic] = station.lastDayTraffic // Вставляем поле lastDayTraffic
                    it[state] = station.state.toString() // Вставляем поле state
                    it[events] = arrayToString(station.events) // Вставляем поле events
                    it[timestamp] = station.timestamp // Вставляем поле timestamp
                    it[qrString] = station.qrString // Вставляем поле qrString
                    it[wallpaper] = station.wallpaper // Вставляем поле wallpaper
                }
            }
        }
    }

    override fun getStationById(stId: String): Station? {
        return transaction {
            // Используем find для поиска записи по stId
//            Stations.select(Stations.stId, *columns.toTypedArray())
//                .where { Stations.stId eq stId }.singleOrNull()?.let { row ->


            Stations.selectAll().where { Stations.stId eq stId }.singleOrNull()?.let { row ->
                // Преобразуем результат в объект Station
                val events = if (row[Stations.events].isBlank()) {
                    ArrayList<JSONObject>()
                } else {
                    try {
                        stringToArray(row[Stations.events])
                    } catch (e: Exception) {
                        AppLogger.error(TAG, "Error parsing events: ${e.message}")
                        ArrayList<JSONObject>()
                    }
                }

                Station(
                    stId = row[Stations.stId],
                    size = row[Stations.size],
                    lastDayTraffic = row[Stations.lastDayTraffic],
                    state = JSONObject(row[Stations.state]),
                    events = events,
                    timestamp = row[Stations.timestamp],
                    qrString = row[Stations.qrString],
                    wallpaper = row[Stations.wallpaper]
                )
            }
        }
    }

    override fun getStations(limit: Int, offset: Int): List<Station> {
        return transaction {
            Stations.selectAll()
                .limit(limit, offset.toLong())
                .map { row ->
                    val events = if (row[Stations.events].isBlank()) {
                        ArrayList<JSONObject>()
                    } else {
                        try {
                            stringToArray(row[Stations.events])
                        } catch (e: Exception) {
                            AppLogger.error(TAG, "Error parsing events: ${e.message}")
                            ArrayList<JSONObject>()
                        }
                    }

                    Station(
                        stId = row[Stations.stId],
                        size = row[Stations.size],
                        lastDayTraffic = row[Stations.lastDayTraffic],
                        state = JSONObject(row[Stations.state]),
                        events = events,
                        timestamp = row[Stations.timestamp],
                        qrString = row[Stations.qrString],
                        wallpaper = row[Stations.wallpaper]
                    )
                }
        }
    }

    private fun arrayToString(jsonArray: ArrayList<JSONObject>): String {
        // Преобразуем ArrayList<JSONObject> в строку
        val jsonArrayString = JSONArray(jsonArray).toString()
        // Здесь мы сохраняем jsonArrayString в базу данных (например, как текстовое поле)
        return jsonArrayString // Возвращаем строку для примера
    }

    private fun stringToArray(jsonString: String): ArrayList<JSONObject> {
        // Преобразуем строку в JSONArray
        val jsonArray = JSONArray(jsonString)
        // Преобразуем JSONArray в ArrayList<JSONObject>
        val jsonObjectList = ArrayList<JSONObject>()
        for (i in 0 until jsonArray.length()) {
            jsonObjectList.add(jsonArray.getJSONObject(i))
        }
        return jsonObjectList
    }
}
