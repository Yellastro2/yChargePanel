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
import kotlinx.html.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import redis.clients.jedis.Jedis


@Serializable
data class Station(val id: String, val name: String)

@Serializable
data class CheckinData(val stId: String, val size: Int, val available: Int)

fun Application.configureRouting() {

//    val proxyHost = "your.proxy.host"
//    val proxyPort = 8080

//    val jedisShardInfo = JedisShardInfo(redisHost, redisPort).apply {
//        this.proxy = Proxy(Proxy.Type.HTTP,
//            InetSocketAddress(proxyHost, proxyPort))
//        this.password = redisPassword
//    }

    val jedis = Jedis("redis-12703.c327.europe-west1-2.gce.redns.redis-cloud.com", 12703)
    jedis.auth("XUqovunbShd12asbuLVoZeYf63DfJNPq")

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


    routing {

        static("/static") { resources("static") }

        get("/api/checkin") {

            val stId = call.request.queryParameters["stId"]
            val size = call.request.queryParameters["size"]?.toIntOrNull()
            val available = call.request.queryParameters["available"]?.toIntOrNull()
            val timeoutHeader = call.request.headers["X-Timeout"]?.toIntOrNull()

            if (stId != null && size != null && available != null && timeoutHeader != null) {
                val checkinData = CheckinData(stId, size, available)
                val timestamp = System.currentTimeMillis() / 1000

                val key = "Stations:${checkinData.stId}"
                val value = mapOf(
                    "stId" to checkinData.stId,
                    "size" to checkinData.size.toString(),
                    "available" to checkinData.available.toString(),
                    "timestamp" to timestamp.toString()
                )

                // Проверка типа данных ключа
                if (jedis.type(key) != "hash") {
                    jedis.del(key) // Удаление ключа, если тип данных отличается
                }

                val exists = jedis.exists(key)
                jedis.hmset(key, value)

                val newEvent = waitForEventOrTimeout(timeoutHeader)

                val response = if (newEvent != null) {
                    JsonObject(mapOf("command" to JsonPrimitive("pong"),"code" to JsonPrimitive(200)))
                }
                else {
                    JsonObject(mapOf("command" to JsonPrimitive("timeout"),"code" to JsonPrimitive(200)))
                }


                var resp = "Station added: ${checkinData.stId}"
                if (exists) {
                    resp = "Station updated: ${checkinData.stId}"
                }

                call.respond(HttpStatusCode.OK, response.toString())
            } else {
                call.respond(HttpStatusCode.BadRequest, "Missing or invalid query parameters")
            }


        }


        get("/api/stationList") {
            val pattern = "Stations:*"
            val keys = jedis.keys(pattern)

            val jsonArray = JsonArray(keys.mapNotNull { key ->
                if (jedis.type(key) != "hash") {
                    jedis.del(key) // Удаление ключа, если тип данных отличается
                    null
                }
                else {
                    val fields = jedis.hgetAll(key)
                    val stId = fields["stId"]
                    val size = fields["size"]?.toIntOrNull()
                    val available = fields["available"]?.toIntOrNull()
                    val timestamp = fields["timestamp"]?.toLongOrNull()



                    if (stId != null && size != null && available != null && timestamp != null) {
                        JsonObject(
                            mapOf(
                                "stId" to JsonPrimitive(stId),
                                "size" to JsonPrimitive(size),
                                "available" to JsonPrimitive(available),
                                "timestamp" to JsonPrimitive(timestamp)
                            )
                        )
                    } else {
                        null
                    }
                }
            })

            call.respond(HttpStatusCode.OK, jsonArray.toString())
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
//            call.respondText("Hello World!")
                val header = readHtml("header.html")
                val body = readHtml("panel.html")
                val footer = readHtml("footer.html")
                val finalPage = "$header\n$body\n$footer"
                call.respondHtml {
                    unsafe { raw(finalPage) }
                }
            }
        }
    }
}
