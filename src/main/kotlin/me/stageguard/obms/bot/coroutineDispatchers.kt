package me.stageguard.obms.bot

import kotlinx.coroutines.asCoroutineDispatcher
import me.stageguard.obms.OsuMapSuggester
import net.mamoe.mirai.utils.debug
import java.util.concurrent.Executors

val THREAD_LOGGER = System.getProperty("me.stageguard.obms.thread_logger", false.toString()).toBoolean()

val graphicProcessorDispatcher = Executors.newFixedThreadPool(20) { runnable ->
    Thread(runnable).also {
        it.name = "Graphics Processor"
        if(THREAD_LOGGER) OsuMapSuggester.logger.debug { "New graphics processor: $it" }
    }
}.asCoroutineDispatcher()

val calculatorProcessorDispatcher = Executors.newFixedThreadPool(15) { runnable ->
    Thread(runnable).also {
        it.name = "Calculator Processor"
        if(THREAD_LOGGER) OsuMapSuggester.logger.debug { "New calculator processor: $it" }
    }
}.asCoroutineDispatcher()