package me.stageguard.obms.frontend.route

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.stageguard.obms.PluginConfig

const val AUTHORIZE_PATH = "authorize"

fun Application.authorize() {
    routing {
        get("/$AUTHORIZE_PATH") {
            val state = context.request.queryParameters["state"]
            if(state != null) {
                context.respondRedirect(buildString {
                    append("https://osu.ppy.sh/oauth/authorize?")

                    append("client_id=")
                    append(PluginConfig.osuAuth.clientId)

                    append("&redirect_uri=")
                    @Suppress("HttpUrlsUsage")
                    append("${PluginConfig.osuAuth.authCallbackBaseUrl}/$AUTH_CALLBACK_PATH")

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
