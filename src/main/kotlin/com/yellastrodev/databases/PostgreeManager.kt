package com.yellastrodev.databases

import com.yellastrodev.databases.Stations.apkVersion
import com.yellastrodev.databases.Stations.blockedSlots
import com.yellastrodev.databases.Stations.events
import com.yellastrodev.databases.Stations.lastDayTraffic
import com.yellastrodev.databases.Stations.qrString
import com.yellastrodev.databases.Stations.size
import com.yellastrodev.databases.Stations.stId
import com.yellastrodev.databases.Stations.state
import com.yellastrodev.databases.Stations.status
import com.yellastrodev.databases.Stations.timestamp
import com.yellastrodev.databases.Stations.wallpaper
import com.yellastrodev.databases.entities.Powerbank
import com.yellastrodev.databases.entities.Station
import com.yellastrodev.yLogger.AppLogger
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.*
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
    val blockedSlots = text("blockedSlots").default("[]")
    val status = enumerationByName("status", 50, Station.Status::class)
    val apkVersion = text("apkVersion").default("0")

    override val primaryKey = PrimaryKey(stId, name = "PK_Stations_stId")
}

object Powerbanks : Table() {
    val id = varchar("id", 255) // Идентификатор повербанка
    val status = enumerationByName("status", 50, Station.Status::class) // Статус повербанка

    override val primaryKey = PrimaryKey(id, name = "PK_Powerbanks_id")
}


class PostgreeManager: DbManager {
    private val TAG = "PostgreeManager"

    private val url3 = "jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:6543/postgres?prepareThreshold=0"

    private val user = "postgres.wqfqhszmsmsyvhjsgjuz"
    private val ypassword = "7821493402Ss"
    private val hikariDataSource: HikariDataSource
    private val driver = "org.postgresql.Driver"


    init {

        // Настройка конфигурации пула
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = url3
            username = user
            password = ypassword
            driverClassName = driver
            maximumPoolSize = 50  // Максимальное количество подключений в пуле
            isAutoCommit = false  // Отключаем автокоммит
            connectionTimeout = 8000 // Максимальное время ожидания подключения
        }

        // Отключаем использование подготовленных запросов и их кэширование
//        hikariConfig.addDataSourceProperty("useServerPrepStmts", false)
//        hikariConfig.addDataSourceProperty("cachePrepStmts", false)
//        hikariConfig.addDataSourceProperty("prepStmtCacheSize", 250)
//        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")

        // Создание DataSource с использованием конфигурации
        hikariDataSource = HikariDataSource(hikariConfig)

        Database.connect(hikariDataSource)

        transaction {
            // Создание таблицы, если она не существует
            SchemaUtils.create(Stations)
            SchemaUtils.create(Powerbanks)
        }
    }

    fun serializeStation(updateStatement: org.jetbrains.exposed.sql.statements.UpdateBuilder<*>, station: Station) {
        updateStatement[stId] = station.stId
        updateStatement[size] = station.size // Обновляем поле size
        updateStatement[lastDayTraffic] = station.lastDayTraffic // Обновляем поле lastDayTraffic
        updateStatement[state] = station.state.toString() // Обновляем поле state
        updateStatement[events] = arrayToString(station.events) // Обновляем поле events
        updateStatement[timestamp] = station.timestamp // Обновляем поле timestamp
        updateStatement[qrString] = station.qrString // Обновляем поле qrString
        updateStatement[wallpaper] = station.wallpaper // Обновляем поле wallpaper
        updateStatement[blockedSlots] = JSONArray(station.blockedSlots.map { it.name }).toString()
        updateStatement[status] = station.status
        updateStatement[apkVersion] = station.apkVersion
    }

    fun deserializeStation(row: ResultRow): Station {
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

        val jsonArray = JSONArray(row[Stations.blockedSlots])

        return Station(
            stId = row[stId],
            size = row[size],
            lastDayTraffic = row[lastDayTraffic],
            state = JSONObject(row[state]),
            events = events,
            timestamp = row[timestamp],
            qrString = row[qrString],
            wallpaper = row[wallpaper],
            blockedSlots = Array(row[size]) { index ->
                Station.Status.valueOf(jsonArray.optString(index, Station.Status.AVAILABLE.toString())) // Десериализация в массив SlotStatus
            },
            status = row[status],
            apkVersion = row[apkVersion]
        )
    }

    override fun updateStation(station: Station) {
        transaction {
            // Проверяем, существует ли станция с таким stId
            val existingStation = Stations.select(Stations.stId).where { Stations.stId eq station.stId }.singleOrNull()

            if (existingStation != null) {
                // Если станция существует, обновляем ее
                Stations.update({ Stations.stId eq station.stId }) {
                    serializeStation(it, station)
                }
            } else {
                // Если станции нет, вставляем новую
                Stations.insert {
                    serializeStation(it, station)
                }
            }
        }
    }

    override fun getStationById(stId: String): Station? {
        return transaction {

            Stations.selectAll().where { Stations.stId eq stId }.singleOrNull()?.let { row ->
                deserializeStation(row)
            }
        }
    }

    override fun getStations(limit: Int, offset: Int, filter: String, onlineSeconds: Int): Pair<List<Station>, Int> {
        return transaction {

            // Основной запрос
            val query = Stations
                .selectAll()

            // Если есть фильтр, применяем его
            if (filter == "online") {
                query.andWhere { Stations.timestamp greaterEq onlineSeconds }
            } else if (filter == "offline") {
                query.andWhere { Stations.timestamp less onlineSeconds }
            }

            val totalCount = query.count().toInt()


            query.limit(limit).offset(offset.toLong())

            query.orderBy(Stations.timestamp to SortOrder.DESC)

            val stations = query.map { station ->
                deserializeStation(station)
            }

            Pair(stations, totalCount)
        }
    }

    override fun getStationCount(fTimestamp: Int): Pair<Int, Int> {
        return transaction {
            // Выполняем два агрегационных запроса с фильтрацией
            val totalCount = Stations.selectAll().count().toInt()
            val filteredCount = Stations.select(Stations.timestamp).where {Stations.timestamp greater fTimestamp }.count().toInt()

            totalCount to filteredCount
        }
    }

    private fun deserializePowerbank(row: ResultRow): Powerbank {
        return Powerbank(
            id = row[Powerbanks.id],
            status = row[Powerbanks.status] //Station.Status.valueOf(row[Powerbanks.status].toString())
        )
    }


    override fun updatePowerbank(powerbank: Powerbank) {
        transaction {
            val existingPowerbank = Powerbanks.select(Powerbanks.id).where { Powerbanks.id eq powerbank.id }.singleOrNull()

            if (existingPowerbank != null) {
                Powerbanks.update({ Powerbanks.id eq powerbank.id }) {
                    it[status] = powerbank.status
                }
            } else {
                Powerbanks.insert {
                    it[id] = powerbank.id
                    it[status] = powerbank.status
                }
            }
        }
    }

    override fun getPowerbankById(id: String): Powerbank? {
        return transaction {
            Powerbanks.selectAll().where { Powerbanks.id eq id }
                .singleOrNull()
                ?.let { row ->
                    deserializePowerbank(row)
                }
        }
    }

    override fun getPowerbanksByIds(ids: List<String>): List<Powerbank> {
        return transaction {
            Powerbanks
                .selectAll().where { Powerbanks.id inList ids }
                .map { row -> deserializePowerbank(row) }
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
