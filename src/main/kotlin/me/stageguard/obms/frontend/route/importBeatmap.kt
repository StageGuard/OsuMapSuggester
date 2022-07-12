package me.stageguard.obms.frontend.route

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

const val IMPORT_BEATMAP_PATH = "import"

fun Application.importBeatmap() {
    routing {
        get("/${IMPORT_BEATMAP_PATH}/{bid}") {
            call.parameters["bid"].run {
                if(this == null) {
                    context.respond(HttpStatusCode.NotFound, "No beatmap id specified.")
                } else try {
                    val bid = this.toInt()
                    context.respondRedirect("osu://b/$bid")
                } catch (ex: NumberFormatException) {
                    context.respond(HttpStatusCode.Forbidden, "Beatmap id must be a number.")
                } catch (ex: Exception) {
                    context.respond(HttpStatusCode.InternalServerError, "Internal error: $ex")
                }
            }
            finish()
        }
    }
}
