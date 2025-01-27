package com.yellastrodev

import com.yellastrodev.yLogger.AppLogger
import com.yellastrodev.ymtserial.KEY_PATH
import com.yellastrodev.ymtserial.PATH_BASE
import com.yellastrodev.ymtserial.ROUT_DOWNLOAD
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureUtilApiRouting() {

    val TAG = "UtilApi"

    routing {
        route("/api") {
            get("/$ROUT_DOWNLOAD") {
                AppLogger.info(TAG, "api/$ROUT_DOWNLOAD")
                val params = extractParametersOrFail(call, listOf(KEY_PATH)) { errorMessage ->
                    call.respondText(errorMessage, status = HttpStatusCode.BadRequest)
                } ?: return@get
                val filePath = params[KEY_PATH]!!
                val file = File("$PATH_BASE/$filePath")
                if (file.exists()) {
                    call.respondFile(file)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Файл не найден")
                }
            }
        }
    }
}