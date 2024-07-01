package me.stageguard.obms.osu.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.stageguard.obms.*
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.InferredOptionalValue
import me.stageguard.obms.utils.OptionalValue
import me.stageguard.obms.utils.info
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.InputStream
import kotlin.properties.Delegates

@Component
class OsuHttpClient {
    val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${clientProxy}")
    private lateinit var clientProxy: String

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout)

            if (clientProxy.isNotBlank()) {
                engine { proxy = ProxyBuilder.http(clientProxy) }
            }
        }
    }

    @Suppress("DuplicatedCode")
    suspend inline fun <reified RESP, reified R> get(
        url: String,
        parameters: Map<String, Any>,
        headers: Map<String, String>,
        crossinline consumer: RESP.() -> R
    ): OptionalValue<R> = withContext(Dispatchers.IO) {
        try {
            client.get {
                url(buildString {
                    append(url)
                    if (parameters.isNotEmpty()) {
                        append("?")
                        parameters.forEach { (k, v) ->
                            if (v is List<*> || v is MutableList<*>) {
                                val arrayParameter = (v as List<*>)
                                arrayParameter.forEachIndexed { idx, lv ->
                                    append("$k[]=$lv")
                                    if(idx != arrayParameter.lastIndex) append("&")
                                }
                            } else {
                                append("$k=$v")
                            }
                            append("&")
                        }
                    }
                }.run {
                    if (last() == '&') dropLast(1) else this
                }.also { logger.info { "GET: $it" } })
                headers.forEach { (s, s2) -> header(s, s2) }
            }.run { InferredOptionalValue(consumer(this.body())) }
        } catch (ex: Exception) {
            when(ex) {
                is RefactoredException -> Either(ex)
                is SocketTimeoutException,
                is HttpRequestTimeoutException,
                is ConnectTimeoutException -> {
                    Either(ApiRequestTimeoutException(url).suppress(ex))
                }
                else -> {
                    Either(UnhandledException(ex))
                }
            }
        }
    }

    @Suppress("DuplicatedCode")
    suspend inline fun head(
        url: String,
        parameters: Map<String, Any>,
        headers: Map<String, String>
    ): OptionalValue<Headers> = withContext(Dispatchers.IO) {
        try {
            client.head {
                url(buildString {
                    append(url)
                    if (parameters.isNotEmpty()) {
                        append("?")
                        parameters.forEach { (k, v) ->
                            if (v is List<*> || v is MutableList<*>) {
                                val arrayParameter = (v as List<*>)
                                arrayParameter.forEachIndexed { idx, lv ->
                                    append("$k[]=$lv")
                                    if(idx != arrayParameter.lastIndex) append("&")
                                }
                            } else {
                                append("$k=$v")
                            }
                            append("&")
                        }
                    }
                }.run {
                    if (last() == '&') dropLast(1) else this
                }.also { logger.info { "HEAD: $it" } })
                headers.forEach { header(it.key, it.value) }
            }.let { InferredOptionalValue(it.headers) }
        } catch (ex: Exception) {
            when(ex) {
                is SocketTimeoutException,
                is HttpRequestTimeoutException,
                is ConnectTimeoutException -> {
                    Either(ApiRequestTimeoutException(url).suppress(ex))
                }
                else -> {
                    Either(UnhandledException(ex))
                }
            }
        }
    }

    @Suppress("DuplicatedCode")
    suspend inline fun <reified REQ, reified RESP> post(
        url: String, token: String? = null, body: @Serializable REQ
    ): OptionalValue<RESP> = withContext(Dispatchers.IO) {
        var responseText by Delegates.notNull<String>()
        try {
            responseText = client.post {
                url(url.also {
                    logger.info { "POST: $url" }
                })
                if(token != null) header("Authorization", "Bearer $token")
                setBody(json.encodeToString(body))
                contentType(ContentType.Application.Json)
            }.body()

            InferredOptionalValue(json.decodeFromString(responseText))
        } catch (ex: Exception) {
            when(ex) {
                is SocketTimeoutException,
                is HttpRequestTimeoutException,
                is ConnectTimeoutException -> {
                    Either(ApiRequestTimeoutException(url).suppress(ex))
                }
                is SerializationException -> {
                    Either(BadResponseException(url, responseText).suppress(ex))
                }
                else -> {
                    Either(UnhandledException(ex))
                }
            }
        }
    }

    @Suppress("DuplicatedCode")
    suspend inline fun openStream(
        url: String,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ) = get<InputStream, InputStream>(url, parameters, headers) { this }

    fun closeClient() = kotlin.runCatching {
        client.close()
    }
}