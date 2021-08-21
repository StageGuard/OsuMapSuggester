package me.stageguard.obms.utils

import net.mamoe.mirai.utils.Either

typealias ValueOrIllegalStateException<T> = Either<IllegalStateException, T>

@Suppress("FunctionName")
inline fun <reified T> InferredEitherOrISE(v: T) = Either.invoke<IllegalStateException, T>(v)