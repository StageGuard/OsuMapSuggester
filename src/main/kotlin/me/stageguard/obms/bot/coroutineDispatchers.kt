package me.stageguard.obms.bot

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.asCoroutineDispatcher
import me.stageguard.obms.OsuMapSuggester
import net.mamoe.mirai.utils.debug
import java.util.concurrent.Executors

val THREAD_LOGGER = System.getProperty("me.stageguard.obms.thread_logger", false.toString()).toBoolean()

val calculatorProcessorDispatcher = MessageRoute.coroutineContext