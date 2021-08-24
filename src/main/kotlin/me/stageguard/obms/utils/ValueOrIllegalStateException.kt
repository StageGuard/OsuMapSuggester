package me.stageguard.obms.utils

typealias ValueOrISE<T> = Either<IllegalStateException, T>

@Suppress("FunctionName")
inline fun <reified T> InferredEitherOrISE(v: T) = Either.invoke<IllegalStateException, T>(v)