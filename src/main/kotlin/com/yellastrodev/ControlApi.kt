package com.yellastrodev

import com.yellastrodev.CommandsManager.sendCommandToStation
import com.yellastrodev.ymtserial.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CompletableFuture



fun Application.configureControlApiRouting() {

    routing {
        route("/api") {


        }
    }
}
