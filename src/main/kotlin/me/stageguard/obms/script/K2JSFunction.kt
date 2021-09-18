package me.stageguard.obms.script

import org.mozilla.javascript.FunctionObject
import org.mozilla.javascript.Scriptable
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

class K2JSFunction(
    name: String, kFunction: KFunction<*>, scope: Scriptable
) : FunctionObject(name, kFunction.javaMethod, scope)