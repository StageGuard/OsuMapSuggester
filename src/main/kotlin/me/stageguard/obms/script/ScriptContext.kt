package me.stageguard.obms.script

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.stageguard.obms.OsuMapSuggester
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import org.mozilla.javascript.*
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

object ScriptContext : CoroutineScope {
    private lateinit var initJob: Job
    private lateinit var ctx: Context
    private lateinit var topLevelScope: ImporterTopLevel
    @OptIn(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
    private val dispatcher = newSingleThreadContext("JavaScriptContext")
    private val lock = Mutex()
    private val exceptionHandler = CoroutineExceptionHandler { _: CoroutineContext, throwable: Throwable ->
        OsuMapSuggester.logger.error { throwable.printStackTrace(); throwable.localizedMessage }
    }
    private val optLv by lazy {
        try {
            Class.forName("android.os.Build"); 0
        } catch (e: Throwable) { -1 }
    }
    private val compileStringPrivMethod = Context::class.java.getDeclaredMethod("compileString",
        String::class.java, Evaluator::class.java, ErrorReporter::class.java,
        String::class.java, Int::class.java, Object::class.java
    ).also { it.trySetAccessible() }

    override val coroutineContext: CoroutineContext
        get() = dispatcher + exceptionHandler

    fun init() {
        initJob = OsuMapSuggester.launch(dispatcher + exceptionHandler) {
            ctx = Context.enter()
            ctx.optimizationLevel = optLv
            ctx.languageVersion = Context.VERSION_ES6
            topLevelScope = ImporterTopLevel()
            ScriptRuntime.initSafeStandardObjects(ctx, topLevelScope, true)
            //init global objects
            //ScriptableObject.putProperty(topLevelScope, "_propertyName", Context.javaToJS(Any(), topLevelScope))
            OsuMapSuggester.logger.info { "JavaScript context initialized." }
        }
    }

    suspend fun putGlobalProperty(name: String, value: Any?) = withContext(coroutineContext) {
        initJob.join()
        ScriptableObject.putProperty(topLevelScope, name, Context.javaToJS(value, topLevelScope))
    }

    suspend fun <R> withProperties(properties: Map<String, Any?>, block: suspend () -> R) = lock.withLock {
        properties.forEach { (name, value) ->
            ScriptableObject.putProperty(topLevelScope, name, Context.javaToJS(value, topLevelScope))
        }
        block().also {
            properties.keys.forEach { name -> ScriptableObject.deleteProperty(topLevelScope, name) }
        }
    }

    fun checkSyntax(src: String) : Pair<Boolean, List<String>> {
        val messages = mutableListOf<String>()
        var isError = false

        kotlin.runCatching {
            Parser(CompilerEnvirons().also {
                it.optimizationLevel = optLv
                it.isGeneratingSource = true
                it.isGenerateDebugInfo = true
            }, object : ErrorReporter {
                override fun warning(
                    message: String?, sourceName: String?,
                    line: Int, lineSource: String?, lineOffset: Int
                ) {
                    messages.add("WARNING: $message in ($line, $lineOffset)")
                }

                override fun error(
                    message: String?, sourceName: String?,
                    line: Int, lineSource: String?, lineOffset: Int
                ) {
                    isError = true
                    messages.add("ERROR: $message in ($line, $lineOffset)")
                }

                override fun runtimeError(
                    message: String?, sourceName: String?,
                    line: Int, lineSource: String?, lineOffset: Int
                ): EvaluatorException {
                    return EvaluatorException(message, sourceName, line, lineSource, lineOffset)
                }
            }).parse(src, "", 0)
        }

        return isError to messages
    }

    suspend fun compile(src: String, errorReporter: ErrorReporter? = null): Script = withContext(dispatcher) {
        compileStringPrivMethod.invoke(ctx, """
            this["__${'$'}internalRunAndGetResult${'$'}"] = eval("${src.replace("\"", "\\\"")}")
        """.trimIndent(), null, errorReporter, "RunJavaScript", 1, null) as Script
    }

    suspend fun <T : Any> evaluateAndGetResult(
        source: String, properties: Map<String, Any?> = mapOf()
    ) = withContext(coroutineContext) {
        initJob.join()
        withProperties(properties) {
            putGlobalProperty("__${'$'}internalRunAndGetResult${'$'}", null)
            compile(source).exec(ctx, topLevelScope)
            topLevelScope["__${'$'}internalRunAndGetResult${'$'}"] as T
        }
    }

    fun createJSFunctionFromKJvmStatic(name: String, kFunction: KFunction<*>) =
        FunctionObject(name, kFunction.javaMethod, topLevelScope)
}