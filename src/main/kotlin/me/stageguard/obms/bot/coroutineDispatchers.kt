package me.stageguard.obms.bot

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.asCoroutineDispatcher
import me.stageguard.obms.OsuMapSuggester
import net.mamoe.mirai.utils.debug
import java.util.concurrent.Executors

val THREAD_LOGGER = System.getProperty("me.stageguard.obms.thread_logger", false.toString()).toBoolean()

private val graphicProcessorDispatcherCounter = atomic(0)
val graphicProcessorDispatcher = Executors.newFixedThreadPool(5) { runnable ->
    Thread(runnable).also {
        it.name = "Graphics Processor #${graphicProcessorDispatcherCounter.getAndIncrement()}"
        if(THREAD_LOGGER) OsuMapSuggester.logger.debug { "New graphics processor: $it" }
    }
}.asCoroutineDispatcher()

private val calculatorProcessorDispatcherCounter = atomic(0)
val calculatorProcessorDispatcher = Executors.newFixedThreadPool(5) { runnable ->
    Thread(runnable).also {
        it.name = "Calculator Processor #${calculatorProcessorDispatcherCounter.getAndIncrement()}"
        if(THREAD_LOGGER) OsuMapSuggester.logger.debug { "New calculator processor: $it" }
    }
}.asCoroutineDispatcher()