package com.yellastrodev

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.html.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import redis.clients.jedis.Jedis
import java.io.File


class RedisSessionStorage(private val jedis: Jedis) : SessionStorage {
    //            override suspend fun write(id: String, data: ByteArray) {
//                jedis.set(id.toByteArray(), data)
//            }

    private val json = Json { prettyPrint = true }


    override suspend fun read(id: String): String {
        val sess = jedis.get(id.toByteArray())
        val sstr = sess.toString()
        return sstr ?: throw IllegalArgumentException("Session $id not found")
    }

    override suspend fun write(id: String, value: String) {
        jedis.set(id, value)
    }

    override suspend fun invalidate(id: String) {
        jedis.del(id.toByteArray()) }
}

fun Application.configureSecurity() {

    @Serializable
    data class UserSession(val name: String, val count: Int) : Principal

    install(Sessions) {

        val jedis = Jedis("redis-12703.c327.europe-west1-2.gce.redns.redis-cloud.com", 12703)
        jedis.auth("XUqovunbShd12asbuLVoZeYf63DfJNPq")

        cookie<UserSession>("SESSION", storage = directorySessionStorage(File("build/.sessions"))){
            cookie.path = "/"
            cookie.maxAgeInSeconds = 60 * 60 * 24 * 7

        }
    }

    install(Authentication) {
        session<UserSession>("auth-session") {
            validate { session ->
                if(!session.name.isNullOrEmpty()) {
                    session
                } else {
                    null
                }
            }
            challenge {
                call.respondRedirect("/login")
            }
        }

        form("auth-form") {
            userParamName = "username"
            passwordParamName = "password"
            validate { credentials ->
                if (credentials.name == "admin" && credentials.password == "123") {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
            challenge {
                call.respondRedirect("/login?error=true")
            }
        }


    }

    routing {

        get("/login") {
            val header = readHtml("header.html")
            val body = readHtml("login.html")
            val footer = readHtml("footer.html")
            val finalPage = "$header\n$body\n$footer"
            val error = call.request.queryParameters["error"]?.toBoolean() ?: false
            val htmlContent =
                if (error) {
                    """
                    <html>
                        <head>
                            <title>Login</title>
                            <link href="/static/css/styles.css" rel="stylesheet" />
                        </head>
                        
                        <body>
                            <script>
                                alert("Login failed! Please check your username and password.");
                            </script> $finalPage <script>
                                document.getElementById('logoutButton').style.display = 'none';
                            </script>
                        </body>
                    
                    </html>"""
                } else {
                    """
                    <html>
                        <head>
                            <title>Login</title>
                            <link href="/static/css/styles.css" rel="stylesheet" />
                        </head>
                        
                        <body> $finalPage <script>
                                document.getElementById('logoutButton').style.display = 'none';
                            </script>
                        </body>
                    </html>"""
                }

            call.respondHtml {
                unsafe { raw(htmlContent) }
            }

//            call.respondHtml {
//                body {
//                    form(action = "/login", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
//                        p {
//                            +"Username:"
//                            textInput(name = "username")
//                        }
//                        p {
//                            +"Password:"
//                            passwordInput(name = "password")
//                        }
//                        p {
//                            submitInput() { value = "Login" }
//                        }
//                    }
//                }
//            }
        }

        authenticate("auth-form") {
            post("/login") {
                val userName = call.principal<UserIdPrincipal>()?.name.toString()
                call.sessions.set(UserSession(name = userName, count = 1))
                call.respondRedirect("/")
            }
        }

        authenticate("auth-session") {
            get("/hello") {
                val userSession = call.principal<UserSession>()
                call.sessions.set(userSession?.copy(count = userSession.count + 1))
                call.respondText("Hello, ${userSession?.name}! Visit count is ${userSession?.count}.")
            }
        }

        get("/logout") {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/login")
        }




//        authenticate("myauth1") {
//            get("/protected/route/basic") {
//                val principal = call.principal<UserIdPrincipal>()!!
//                call.respondText("Hello ${principal.name}")
//            }
//        }
//        authenticate("myauth2") {
//            get("/protected/route/form") {
//                val principal = call.principal<UserIdPrincipal>()!!
//                call.respondText("Hello ${principal.name}")
//            }
//        }
    }


}
