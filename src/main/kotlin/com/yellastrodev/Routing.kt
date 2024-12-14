package com.yellastrodev

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.html.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.json.JSONArray
import org.json.JSONObject
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPool
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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


    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    fun waitForEventOrTimeout(timeout: Int): String? {
        // Логика ожидания нового события или истечения таймаута
        // Возвращает событие или null при таймауте
        // Например, использование Thread.sleep(timeout * 1000) для эмуляции задержки
        Thread.sleep((timeout - 5) * 1000L)
        return null
    }

    val waitMap = ConcurrentHashMap<String, CompletableFuture<JsonObject?>>()

    suspend fun waitForEventOrTimeout(stId: String, timeout: Int): JsonObject? {
        val future = waitMap.computeIfAbsent(stId) { CompletableFuture<JsonObject?>() }
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

    fun createCommanJson(fComand: JsonObject): JsonObject {
        return JsonObject(mapOf(
            "command" to JsonPrimitive(fComand.keys.first()),
            "value" to fComand.values.first()
        ))
    }

    routing {

        static("/static") { resources("static") }

        get("/api/checkin") {
            val jedis = jedisPool.resource
            try {
                val stId = call.request.queryParameters["stId"]
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
                        "stId" to stId,
                        "timestamp" to timestamp.toString()
                    )
                    size?.let { value["size"] = it.toString() }

                    trafficLastDay?.let { value["trafficLastDay"] = it }

                    events?.let {
                        val fEventsJSON = JSONArray(it)
                        val fEventsSort = fEventsJSON.sortedBy {
                            (it as JSONObject).getInt("date")
                        }

                        val stateJSON = state?.let { JSONObject(it) }?: run {
                            if (jedis.type(key) != "hash") {
                                jedis.del(key)
                                return@run JSONObject()
                            }
                            val fields = jedis.hgetAll(key)
                            fields["state"]?. let { itStateFromRedis ->
                                JSONObject(itStateFromRedis)
                            }?: JSONObject()
                        }
                        val stateStringPrevius = stateJSON.toString()

                        fEventsSort.forEach {
                            val qEvent = it as JSONObject
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

                        if (stateStringPrevius != stateJSON.toString()) {
                            state = stateJSON.toString()
                            print("NEW EVENT RECEIVED")
                        }
                        //TODO
                    }


                    state?.let { value["state"] = it }


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
                        JsonObject(mapOf("command" to JsonPrimitive("pong"), "code" to JsonPrimitive(200)))
                    }


                    var resp = "Station added: ${stId}"
                    if (exists) {
                        resp = "Station updated: ${stId}"
                    }

                    call.respond(HttpStatusCode.OK, response.toString())
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Missing or invalid query parameters")
                }
            }catch (e: Exception){
                e.printStackTrace()
            }finally {
                jedis.close()
            }
        }

        get("/api/release") {
            val stId = call.request.queryParameters["stId"]
            val num = call.request.queryParameters["num"]
            if (stId != null && num != null) {

                waitMap[stId]?.complete(JsonObject(mapOf("release" to JsonPrimitive(num))))
                call.respond(HttpStatusCode.OK, """{"status": "released"}""")
            } else {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing 'stId' or 'num' parameter")
            }
        }


        get("/api/stationList") {
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
                        val stId = fields["stId"]
                        val size = fields["size"]?.toIntOrNull()
                        val available = fields["state"]?.let{ Json.parseToJsonElement(it).jsonObject.size }?: null
                        val timestamp = fields["timestamp"]?.toLongOrNull()
                        val trafficLastDay = fields["trafficLastDay"]



                        if (stId != null && size != null && available != null && timestamp != null) {
                            JsonObject(
                                mapOf(
                                    "stId" to JsonPrimitive(stId),
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
            }catch (e: Exception){
                e.printStackTrace()
            }finally {
                jedis.close()
            }

        }

        get("/api/stationInfo") {
            val jedis = jedisPool.resource
            try {
                val stId = call.parameters["stId"]
                val key = "Stations:${stId}"

//                val key = jedis.keys(pattern)

//                val jsonArray = JsonArray(keys.mapNotNull { key ->
                if (jedis.type(key) != "hash") {
                    jedis.del(key) // Удаление ключа, если тип данных отличается
                    call.respond(HttpStatusCode.OK, "station ${stId} not found")
                } else {
                    val fields = jedis.hgetAll(key)
                    val stId = fields["stId"]
                    val size = fields["size"]?.toIntOrNull()
                    val state = fields["state"]?.let{ Json.parseToJsonElement(it).jsonObject}
                    val timestamp = fields["timestamp"]?.toLongOrNull()
                    val trafficLastDay = fields["trafficLastDay"]



                    if (stId != null && size != null && state != null && timestamp != null) {
                        val fStationJson = JsonObject(
                            mapOf(
                                "stId" to JsonPrimitive(stId),
                                "size" to JsonPrimitive(size),
                                "state" to state,
                                "timestamp" to JsonPrimitive(timestamp),
                                "trafficLastDay" to JsonPrimitive(trafficLastDay)
                            )
                        )
                        call.respond(HttpStatusCode.OK, fStationJson.toString())
                    } else {
                        call.respond(HttpStatusCode.OK, "somethk wrong")
                    }
                }

//                call.respond(HttpStatusCode.OK, jsonArray.toString())
            }catch (e: Exception){
                e.printStackTrace()
            }finally {
                jedis.close()
            }

        }



        authenticate("auth-session") {
            get("/admin") {
                val header = readHtml("header.html")
                val body = readHtml("panel.html")
                val footer = readHtml("footer.html")
                val finalPage = "$header\n$body\n$footer"
                call.respondHtml {
                    unsafe { raw(finalPage) }
                }
            }
            get("/") {
                val header = readHtml("header.html")
                val body = readHtml("panel.html")
                val footer = readHtml("footer.html")
                val finalPage = "$header\n$body\n$footer"
                call.respondHtml {
                    unsafe { raw(finalPage) }
                }
            }

            get("/station/{stId}") {
                val stId = call.parameters["stId"]

                val header = readHtml("header.html")
                val body = readHtml("station.html")
                val footer = readHtml("footer.html")
                val finalPage = "$header\n$body\n$footer"
                call.respondHtml {
                    unsafe { raw(finalPage) }
                }

            }
        }
    }
}
