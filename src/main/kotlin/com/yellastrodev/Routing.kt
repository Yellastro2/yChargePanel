package com.yellastrodev

import com.yellastrodev.ymtserial.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.html.*
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.json.JSONArray
import org.json.JSONObject
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPool
import java.io.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class Station(val id: String, val name: String)

@Serializable
data class CheckinData(val stId: String, val size: Int, val state: String)

fun Application.configureRouting() {

//    val proxyHost = "your.proxy.host"
//    val proxyPort = 8080

//    val jedisShardInfo = JedisShardInfo(redisHost, redisPort).apply {
//        this.proxy = Proxy(Proxy.Type.HTTP,
//            InetSocketAddress(proxyHost, proxyPort))
//        this.password = redisPassword
//    }


    val TIMEOUT_LOG_UPLOAD = 60L

    val REDIS_HOST = "redis-12703.c327.europe-west1-2.gce.redns.redis-cloud.com"
    val REDIS_PORT = 12703
    val REDIS_PAS = "XUqovunbShd12asbuLVoZeYf63DfJNPq"


    val jedisPool = JedisPool(
        JedisPoolConfig().apply {
            maxTotal = 30 // Максимальное количество соединений в пуле
            maxIdle = 15 // Максимальное количество неиспользуемых соединений в пуле
            maxWaitMillis = 3000L // Максимальное время ожидания соединения
        },
        REDIS_HOST,
        REDIS_PORT,
        6000,
        REDIS_PAS)

    val MAX_EVENTS_SIZE = 100


    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }


    // Карта для хранения колбеков
    val uploadLogsCallbacks = mutableMapOf<String, CompletableFuture<Unit>>()
    val waitMap = ConcurrentHashMap<String, CompletableFuture<JSONObject?>>()

    suspend fun waitForEventOrTimeout(stId: String, timeout: Int): JSONObject? {
        val future = waitMap.computeIfAbsent(stId) { CompletableFuture<JSONObject?>() }
        return try {
            withTimeoutOrNull((if (timeout > 5) timeout - 5 else 0).toLong() * 1000) {
                future.await()
            }
        }catch (e: TimeoutCancellationException) {
             null
        } catch (e: TimeoutException) {
            null
        } finally {
            waitMap.remove(stId)
        }
    }

    fun createCommanJson(fComand: JSONObject): JSONObject {
        val fKey = fComand.keys().next()
        return JSONObject(mapOf(
            "command" to fKey,
            "value" to fComand[fKey]
        ))
    }


    fun sendCommandToStation(stId: String?, commandJSON: JSONObject) {
        waitMap[stId]?.complete(commandJSON)
    }

    fun setWallpaper(stId: String?, newFileName: String) {
        // поменять значение обоев в базе данных + отправить команду станции о смене обоев.
        // если даже команда не дойдет (мб офлайн), станция всёравно замет что id обоев стали разными локально и в базе

        val jedis = jedisPool.resource
        try {
            val key = "Stations:${stId}"
            val fields = jedis.hgetAll(key)
            val oldFileName = fields[CMD_CHANGE_WALLPAPER]
            if (oldFileName != newFileName) {
                fields[CMD_CHANGE_WALLPAPER] = newFileName
                jedis.hset(key, CMD_CHANGE_WALLPAPER, newFileName)
                sendCommandToStation(stId,JSONObject(mapOf(CMD_CHANGE_WALLPAPER to newFileName)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            jedis.close()
        }
    }


    routing {


        route("/api") {
            get("/$ROUT_CHECKIN") {
                val jedis = jedisPool.resource
                try {
                    val stId = call.request.queryParameters[KEY_STATION_ID]
                    val size = call.request.queryParameters["size"]?.toIntOrNull()
                    var state = call.request.queryParameters["state"]
                    val timeoutHeader = call.request.headers["X-Timeout"]?.toIntOrNull()
                    val trafficLastDay = call.request.queryParameters["traffic"]
                    val events = call.request.queryParameters["events"]
                    print(trafficLastDay)

                    if (stId != null && timeoutHeader != null) {
//                    val checkinData = CheckinData(stId, size, state)
                        val timestamp = System.currentTimeMillis() / 1000

                        val key = "Stations:${stId}"
                        val value = hashMapOf(
                            KEY_STATION_ID to stId,
                            "timestamp" to timestamp.toString()
                        )
                        size?.let { value["size"] = it.toString() }

                        trafficLastDay?.let { value["trafficLastDay"] = it }

                        events?.let {
                            val fEventsJSON = JSONArray(it)
                            val fEventsSort = fEventsJSON.sortedBy {
                                (it as JSONObject).getInt("date")
                            }

                            val fields = jedis.hgetAll(key)

                            var fEvents = fields["events"] ?: "[]"
                            if (fEvents == "")
                                fEvents = "[]"
                            val fPreviusEvents = JSONArray(fEvents)

                            val stateJSON = state?.let { JSONObject(it) } ?: run {
                                if (jedis.type(key) != "hash") {
                                    jedis.del(key)
                                    return@run JSONObject()
                                }

                                fields["state"]?.let { itStateFromRedis ->
                                    JSONObject(itStateFromRedis)
                                } ?: JSONObject()
                            }
                            val stateStringPrevius = stateJSON.toString()

                            fEventsSort.forEach {
                                val qEvent = it as JSONObject
                                fPreviusEvents.put(qEvent)
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

                            value["events"] = JSONArray(fPreviusEvents).toString()

                            if (stateStringPrevius != stateJSON.toString()) {
                                state = stateJSON.toString()
                                print("NEW EVENT RECEIVED")
                            }
                            //TODO
                        }

                        var onlineState: JSONObject = JSONObject()

                        state?.let {
                            value["state"] = it
                            val fields = jedis.hgetAll(key)
                            val stateJSON = JSONObject(fields)
                            if (stateJSON.has("wallpaper"))
                                onlineState = JSONObject(
                                    mapOf("wallpaper" to stateJSON.getString("wallpaper"))
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
//                        JsonObject(mapOf("command" to JsonPrimitive("pong"), "code" to JsonPrimitive(200)))
                        } else {
                            JSONObject(mapOf("command" to "pong", "code" to 200))
                        }

                        if (onlineState.length() > 0) {
                            response.put("onlineState", onlineState)
                        }


                        var resp = "Station added: ${stId}"
                        if (exists) {
                            resp = "Station updated: ${stId}"
                        }

                        call.respond(HttpStatusCode.OK, response.toString())
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "Missing or invalid query parameters")
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    jedis.close()
                }
            }

            get("/$ROUT_RELEASE") {
                val stId = call.request.queryParameters[KEY_STATION_ID]
                val num = call.request.queryParameters["num"]
                if (stId != null && num != null) {

//                waitMap[stId]?.complete(JsonObject(mapOf("release" to JsonPrimitive(num))))
                    sendCommandToStation(stId, JSONObject(mapOf("release" to num)))
                    call.respond(HttpStatusCode.OK, """{"status": "released"}""")
                } else {

                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing 'stId' or 'num' parameter")
                }
            }




            get("/$ROUT_DOWNLOAD") {
                val filePath = call.request.queryParameters["path"]
                if (filePath != null) {
                    val file = File("$PATH_BASE/$filePath")
                    if (file.exists()) {
                        call.respondFile(file)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Файл не найден")
                    }
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Параметр 'path' отсутствует")
                }
            }

            post("/$ROUT_UPLOAD_WALLPAPER/{$KEY_STATION_ID}") {
                val stId = call.parameters[KEY_STATION_ID]
                val uploadDir = File(PATH_WALLPAPERS)
                if (!uploadDir.exists()) {
                    uploadDir.mkdirs()
                }

                // Найти максимальный номер файла
                val maxFileNumber = uploadDir.listFiles()?.mapNotNull {
                    it.name.substringBeforeLast(".").toIntOrNull()
                }?.maxOrNull() ?: 0
                val newFileName = "${maxFileNumber + 1}.jpg"

                val multipart = call.receiveMultipart()
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val fileBytes = part.provider.invoke().readRemaining().readByteArray()
                        File(uploadDir, newFileName).writeBytes(fileBytes)
                    }
                    part.dispose()
                }

                setWallpaper(stId, newFileName)

                call.respondRedirect("/station/${stId}")
            }

            post("/$ROUT_SET_QR/{stId}") {
                val stId = call.parameters[KEY_STATION_ID]
                try {

                    val QRString = call.receiveParameters()["text"]

                    jedisPool.resource.use { jedis ->
                        val key = "Stations:${stId}"
                        val fields = jedis.hgetAll(key)
                        val oldQr = fields[CMD_CHANGE_QR]
                        if (oldQr != QRString) {
                            jedis.hset(key, CMD_CHANGE_QR, QRString)
                            sendCommandToStation(stId, JSONObject(mapOf(CMD_CHANGE_QR to QRString)))
                        }
                    }


                    call.respondRedirect("/station/${stId}")
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respondRedirect("/station/${stId}")
                }
            }

            get("/$ROUT_GET_LOGS") {
                val stId = call.request.queryParameters[KEY_STATION_ID]
                val uploadDir = File("${PATH_LOGFILES}/${stId}")
                val lastLogFile = findLatestLogFile(uploadDir)?.name ?: "0"
                if (stId != null) {
                    // TODO_1
                    // Проверяем, на связи ли клиент
                    val isClientConnected = waitMap.containsKey(stId)
                    if (isClientConnected) {
                        // Создаем CompletableFuture для загрузки логов
                        val uploadFuture = CompletableFuture<Unit>()
                        uploadLogsCallbacks[stId] = uploadFuture

                        // Отправляем команду на получение логов
                        sendCommandToStation(stId, JSONObject(mapOf(CMD_GETLOGS to lastLogFile)))
//                    waitMap[stId]?.complete(JsonObject(mapOf(CMD_GETLOGS to JsonPrimitive(lastLogFile))))

                        // Ожидаем загрузки логов
                        val uploadCompleted = withTimeoutOrNull(TIMEOUT_LOG_UPLOAD * 1000) {
                            uploadLogsCallbacks[stId]?.await()
                        }
                        if (uploadCompleted != null) {
                            print("""{"status": "logs uploaded"}""")
//                        call.respond(HttpStatusCode.OK, """{"status": "logs uploaded"}""")
                        } else {
                            print("""{"status": "upload timeout"}""")
//                        call.respond(HttpStatusCode.RequestTimeout, """{"status": "upload timeout"}""")
                        }
                    } else {
                        // Клиент не на связи, сразу отвечаем
                        print("""{"status": "client not connected"}""")
//                    call.respond(HttpStatusCode.OK, """{"status": "client not connected"}""")
                    }
                    val zipFile = getLastLogZip(stId)
                    call.respondFile(zipFile)
//                waitMap[stId]?.complete(JsonObject(mapOf(CMD_GETLOGS to JsonPrimitive(lastLogFile))))
//                call.respond(HttpStatusCode.OK, """{"status": "getting.."}""")
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing 'stId'")
                }
            }


            get("/$ROUT_STATIONLIST") {
                val jedis = jedisPool.resource
                try {
                    val pattern = "Stations:*"

                    val keys = jedis.keys(pattern)

                    val jsonArray = JsonArray(keys.mapNotNull { key ->
                        if (jedis.type(key) != "hash") {
                            jedis.del(key) // Удаление ключа, если тип данных отличается
                            null
                        } else {
                            val fields = jedis.hgetAll(key)
                            val stId = fields[KEY_STATION_ID]
                            val size = fields["size"]?.toIntOrNull()
                            val available = fields["state"]?.let { Json.parseToJsonElement(it).jsonObject.size } ?: null
                            val timestamp = fields["timestamp"]?.toLongOrNull()
                            val trafficLastDay = fields["trafficLastDay"]



                            if (stId != null && size != null && available != null && timestamp != null) {
                                JsonObject(
                                    mapOf(
                                        KEY_STATION_ID to JsonPrimitive(stId),
                                        "size" to JsonPrimitive(size),
                                        "available" to JsonPrimitive(available),
                                        "timestamp" to JsonPrimitive(timestamp),
                                        "trafficLastDay" to JsonPrimitive(trafficLastDay)
                                    )
                                )
                            } else {
                                null
                            }
                        }
                    })

                    call.respond(HttpStatusCode.OK, jsonArray.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    jedis.close()
                }

            }

            get("/$ROUT_STATIONINFO") {
                val jedis = jedisPool.resource
                try {
                    val stId = call.parameters[KEY_STATION_ID]
                    val key = "Stations:${stId}"

//                val key = jedis.keys(pattern)

//                val jsonArray = JsonArray(keys.mapNotNull { key ->
                    if (jedis.type(key) != "hash") {
                        jedis.del(key) // Удаление ключа, если тип данных отличается
                        call.respond(HttpStatusCode.OK, "station ${stId} not found")
                    } else {
                        val fields = jedis.hgetAll(key)
                        val stId = fields[KEY_STATION_ID]
                        val size = fields["size"]?.toIntOrNull()
                        val state = fields["state"]?.let { JSONObject(it) }
                        val timestamp = fields["timestamp"]?.toLongOrNull()
                        val trafficLastDay = fields["trafficLastDay"]



                        if (stId != null && size != null && state != null && timestamp != null) {
                            val fStationJson = JSONObject(
                                mapOf(
                                    KEY_STATION_ID to stId,
                                    "size" to size,
                                    "state" to state,
                                    "timestamp" to timestamp,
                                    "trafficLastDay" to trafficLastDay
                                )
                            )
                            fields["events"]?.let {
                                fStationJson.put("events", JSONArray(if (it != "") it else "[]"))
                            }
                            call.respond(HttpStatusCode.OK, fStationJson.toString())
                        } else {
                            call.respond(HttpStatusCode.OK, "somethk wrong")
                        }
                    }

//                call.respond(HttpStatusCode.OK, jsonArray.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    jedis.close()
                }
            }

            post("/$ROUT_UPLOADLOGS") {
                val stId = call.request.queryParameters.get(KEY_STATION_ID)
                val multipart = call.receiveMultipart()
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val fileBytes = part.provider.invoke().readRemaining().readByteArray()
                        val uploadDir = File("${PATH_LOGFILES}/${stId}")
                        if (!uploadDir.exists()) {
                            uploadDir.mkdirs() // создаем директорию, если она не существует
                        }
                        val zipFile = File(uploadDir, part.originalFileName ?: "uploaded-archive.zip")
                        zipFile.writeBytes(fileBytes)
                        extractZip(zipFile, uploadDir)
                        part.dispose()
                    }
                }

                // Вызываем колбек, если он существует
                uploadLogsCallbacks[stId]?.complete(Unit)
                uploadLogsCallbacks.remove(stId)

                call.respondText("Archive uploaded and extracted successfully")
            }

        }


    }
}


