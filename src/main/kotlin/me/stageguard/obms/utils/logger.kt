package me.stageguard.obms.utils

import org.slf4j.Logger

fun Logger.info(lazyMsg: () -> String) = info(lazyMsg())

fun Logger.warning(lazyMsg: () -> String) = warn(lazyMsg())

fun Logger.error(lazyMsg: () -> String) = error(lazyMsg())

fun Logger.debug(lazyMsg: () -> String) = debug(lazyMsg())