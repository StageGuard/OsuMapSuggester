package me.stageguard.obms.utils

import me.stageguard.obms.RefactoredException

typealias OptionalValue<T> = Either<RefactoredException, T>

@Suppress("FunctionName")
inline fun <reified T> InferredOptionalValue(v: T) = Either.invoke<RefactoredException, T>(v)