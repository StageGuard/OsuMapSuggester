package me.stageguard.obms.utils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


@OptIn(ExperimentalContracts::class)
inline fun <R> retry(
    n: Int,
    exceptionBlock: (Throwable) -> Unit = { },
    block: (Int) -> R
): Result<R> {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
    }
    require(n >= 1) { "param n for retryCatching must not be negative" }
    var timesRetried = 0
    var exception: Throwable? = null
    repeat(n) {
        try {
            return Result.success(block(timesRetried++))
        } catch (e: Throwable) {
            exceptionBlock(e)
            exception?.addSuppressed(e)
            exception = e
        }
    }
    return Result.failure(exception!!)
}