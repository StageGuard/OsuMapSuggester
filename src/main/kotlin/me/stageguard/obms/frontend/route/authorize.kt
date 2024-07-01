package me.stageguard.obms.frontend.route

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

const val AUTHORIZE_PATH = "authorize"

fun Application.authorize(clientId: Int, authCallbackBaseUrl: String) {
    routing {
        get("/$AUTHORIZE_PATH") {
            val state = context.request.queryParameters["state"]
            if(state != null) {
                context.respondRedirect(buildString {
                    append("https://osu.ppy.sh/oauth/authorize?")

                    append("client_id=")
                    append(clientId)

                    append("&redirect_uri=")
                    @Suppress("HttpUrlsUsage")
                    append("$authCallbackBaseUrl/$AUTH_CALLBACK_PATH")

                    append("&response_type=code")
                    append("&scope=identify%20%20friends.read%20%20public")

                    append("&state=$state")
                })
            } else {
                context.respond(HttpStatusCode.Forbidden, "Parameter \"state\" is missing.")
            }
        }
    }
}
