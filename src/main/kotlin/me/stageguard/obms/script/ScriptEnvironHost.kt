package me.stageguard.obms.script

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.utils.error
import me.stageguard.obms.utils.info
import org.mozilla.javascript.*
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

object ScriptEnvironHost : CoroutineScope {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private lateinit var initJob: Job
    private lateinit var ctx: Context
    private lateinit var topLevelScope: ImporterTopLevel
    @OptIn(DelicateCoroutinesApi::class)
    private val dispatcher = newSingleThreadContext("JavaScriptContext")
    private val lock = Mutex()
    private val exceptionHandler = CoroutineExceptionHandler { _: CoroutineContext, throwable: Throwable ->
        logger.error { throwable.printStackTrace(); throwable.localizedMessage }
    }

    private val compileStringPrivMethod = Context::class.java.getDeclaredMethod("compileString",
        String::class.java, Evaluator::class.java, ErrorReporter::class.java,
        String::class.java, Int::class.java, Object::class.java
    ).also { it.trySetAccessible() }

    override val coroutineContext: CoroutineContext
        get() = dispatcher + exceptionHandler

    fun init() {
        initJob = OsuMapSuggester.scope.launch(dispatcher + exceptionHandler) {
            ctx = TEDetectContextFactory.enterContext()
            topLevelScope = ImporterTopLevel()
            //ScriptRuntime.initSafeStandardObjects(ctx, topLevelScope, true)
            ScriptRuntime.initStandardObjects(ctx, topLevelScope, true)
            logger.info { "JavaScript context initialized." }
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
            Parser(CompilerEnvirons().apply {
                optimizationLevel = optLv
                isGeneratingSource = true
                isGenerateDebugInfo = true
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

    fun compile(src: String, errorReporter: ErrorReporter? = null): Script =
        compileStringPrivMethod.invoke(ctx, """
            this["__${'$'}internalRunAndGetResult${'$'}"] = eval("${src.replace("\"", "\\\"")}")
        """.trimIndent(), null, errorReporter, "RunJavaScript", 1, null) as Script

    suspend fun <T : Any> evaluateAndGetResult(
        source: String, properties: Map<String, Any?> = mapOf()
    ) = withContext(coroutineContext) scriptContext@ {
        initJob.join()
        withProperties(properties) {
            putGlobalProperty("__${'$'}internalRunAndGetResult${'$'}", null)
            runInterruptible(this@scriptContext.coroutineContext) { compile(source).exec(ctx, topLevelScope) }
            @Suppress("UNCHECKED_CAST")
            topLevelScope["__${'$'}internalRunAndGetResult${'$'}"] as T
        }
    }

    fun createJSFunctionFromKJvmStatic(name: String, kFunction: KFunction<*>) =
        FunctionObject(name, kFunction.javaMethod, topLevelScope)
}