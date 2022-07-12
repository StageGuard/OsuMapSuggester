package me.stageguard.obms.frontend.route

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import me.stageguard.obms.OsuMapSuggester
import java.io.InputStream
import java.nio.charset.Charset

const val FRONTEND_RES_PATH = "static"

fun getFrontendResourceStream(path: String): InputStream? =
    OsuMapSuggester.javaClass.getResourceAsStream("/static/$path")

fun Application.frontendResource() {
    routing {
        get("/$FRONTEND_RES_PATH/{path...}") {
            val path = call.parameters.getAll("path")!!.joinToString("/")
            val stream = getFrontendResourceStream(path)
            if(stream == null) {
                context.respond(HttpStatusCode.NotFound, "Resource $path is not found.")
            } else runInterruptible(Dispatchers.IO) { runBlocking {
                context.respond(
                    HttpStatusCode.OK,
                    stream.readAllBytes().toString(Charset.forName("UTF-8"))
                )
            } }
        }
    }
}
