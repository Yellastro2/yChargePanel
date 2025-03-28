package com.yellastrodev

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.unsafe

fun Application.configureWebRouting() {

    routing {

        static("/static") { resources("static") }

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
                call.respondRedirect("/stations/1")
            }


            get("/stations/{page?}") {
                val page = call.parameters["page"] ?: "1" // Получаем текущую страницу, по умолчанию "1"
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