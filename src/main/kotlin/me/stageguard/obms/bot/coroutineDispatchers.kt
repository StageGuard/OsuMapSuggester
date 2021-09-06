package me.stageguard.obms.bot

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.asCoroutineDispatcher
import me.stageguard.obms.OsuMapSuggester
import net.mamoe.mirai.utils.debug
import java.util.concurrent.Executors

val THREAD_LOGGER = System.getProperty("me.stageguard.obms.thread_logger", false.toString()).toBoolean()

val graphicProcessorDispatcher = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable).also {
        it.name = "OBMS Graphics"
        if(THREAD_LOGGER) OsuMapSuggester.logger.debug { "New graphics thread: $it" }
    }
}.asCoroutineDispatcher()

val calculatorProcessorDispatcher = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable).also {
        it.name = "OBMS Calculator"
        if(THREAD_LOGGER) OsuMapSuggester.logger.debug { "New calculator thread: $it" }
    }
}.asCoroutineDispatcher()

val networkProcessorDispatcher = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable).also {
        it.name = "OBMS Network"
        if(THREAD_LOGGER) OsuMapSuggester.logger.debug { "New network thread: $it" }
    }
}.asCoroutineDispatcher()