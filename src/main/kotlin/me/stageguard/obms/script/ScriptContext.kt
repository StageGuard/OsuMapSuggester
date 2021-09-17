package me.stageguard.obms.script

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.onLeft
import me.stageguard.obms.utils.Either.Companion.onRight
import me.stageguard.obms.utils.InferredEitherOrISE
import me.stageguard.obms.utils.ValueOrISE
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import org.mozilla.javascript.Context
import org.mozilla.javascript.ImporterTopLevel
import org.mozilla.javascript.Script
import org.mozilla.javascript.ScriptableObject
import kotlin.coroutines.CoroutineContext

object ScriptContext : CoroutineScope {
    private lateinit var initJob: Job
    private lateinit var ctx: Context
    private lateinit var topLevelScope: ImporterTopLevel
    @OptIn(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
    private val dispatcher = newSingleThreadContext("JavaScriptContext")
    private val lock = Mutex()
    private val exceptionHandler = CoroutineExceptionHandler { _: CoroutineContext, throwable: Throwable ->
        OsuMapSuggester.logger.error { throwable.toString() }
    }

    override val coroutineContext: CoroutineContext
        get() = dispatcher + exceptionHandler

    fun init() {
        initJob = OsuMapSuggester.launch(dispatcher + exceptionHandler) {
            ctx = Context.enter()
            ctx.optimizationLevel = try {
                Class.forName("android.os.Build"); 0
            } catch (e: Throwable) { -1 }
            ctx.languageVersion = Context.VERSION_ES6
            topLevelScope = ImporterTopLevel(ctx).also { it.initStandardObjects(ctx, true) }
            //init global objects
            //ScriptableObject.putProperty(topLevelScope, "_propertyName", Context.javaToJS(Any(), topLevelScope))
            OsuMapSuggester.logger.info { "JavaScript context initialized." }
        }
    }

    suspend fun putGlobalProperty(name: String, value: Any?) = withContext(coroutineContext) {
        initJob.join()
        ScriptableObject.putProperty(topLevelScope, name, Context.javaToJS(value, topLevelScope))
    }

    suspend fun <R> withProperties(properties: Map<String, Any>, block: suspend () -> R) = lock.withLock {
        properties.forEach { (name, value) ->
            ScriptableObject.putProperty(topLevelScope, name, Context.javaToJS(value, topLevelScope))
        }
        block().also {
            properties.keys.forEach { name -> ScriptableObject.deleteProperty(topLevelScope, name) }
        }
    }

    fun compile(src: String) = try {
        ctx.compileString("""
            this["__${'$'}internalRunAndGetResult${'$'}"] = eval("${src.replace("\"", "\\\"")}")
        """.trimIndent(), "RunJavaScript", 1, null)
    } catch (ex: Exception) {
        throw IllegalStateException("JS_COMPILE_ERROR:$ex")
    }

    suspend fun <T : Any> evaluateAndGetResult(
        source: String, properties: Map<String, Any>
    ) = withContext(coroutineContext) {
        initJob.join()
        withProperties(properties) {
            try {
                putGlobalProperty("__${'$'}internalRunAndGetResult${'$'}", null)
                compile(source).exec(ctx, topLevelScope)
                topLevelScope["__${'$'}internalRunAndGetResult${'$'}"] as? T
            } catch (ex: IllegalStateException) {
                throw ex
            } catch (ex: Exception) {
                throw Exception("JS_RUNTIME_ERROR: $ex")
            }
        }
    }
}