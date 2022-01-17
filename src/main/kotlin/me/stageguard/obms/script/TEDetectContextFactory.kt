package me.stageguard.obms.script

import net.mamoe.mirai.console.util.cast
import org.mozilla.javascript.*


object TEDetectContextFactory : ContextFactory() {

    init { initGlobal(this) }

    override fun makeContext() = TEContext().apply { instructionObserverThreshold = 1000 }

    override fun observeInstructionCount(cx: Context, instructionCount: Int) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - cx.cast<TEContext>().startTime > 1000) {
            throw ScriptTimeoutException(1000)
        }
    }

    override fun doTopCall(
        callable: Callable?, cx: Context, scope: Scriptable?, thisObj: Scriptable?, args: Array<Any?>?
    ): Any {
        cx.cast<TEContext>().startTime = System.currentTimeMillis()
        return super.doTopCall(callable, cx, scope, thisObj, args)
    }
}