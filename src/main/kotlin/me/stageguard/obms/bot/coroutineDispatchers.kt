package me.stageguard.obms.bot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.stageguard.obms.OsuMapSuggester

val THREAD_LOGGER = System.getProperty("me.stageguard.obms.thread_logger", false.toString()).toBoolean()

val calculatorProcessorDispatcher = CoroutineScope(
    OsuMapSuggester.scope.coroutineContext + Dispatchers.Unconfined
).coroutineContext