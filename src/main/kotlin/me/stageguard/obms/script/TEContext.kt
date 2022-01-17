package me.stageguard.obms.script

import org.mozilla.javascript.Context

val optLv by lazy {
    try {
        Class.forName("android.os.Build"); 0
    } catch (e: Throwable) { -1 }
}

class TEContext : Context(TEDetectContextFactory) {
    var startTime: Long = 0
    init {
        optimizationLevel = optLv
        languageVersion = VERSION_ES6
    }
}