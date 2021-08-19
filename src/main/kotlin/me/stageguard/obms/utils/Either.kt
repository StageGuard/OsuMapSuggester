package me.stageguard.obms.utils

sealed class Either<out A, out B> private constructor(
    val left: A? = null, val right: B? = null
) {
    class Left<A>(val value: A): Either<A, Nothing>(left = value)
    class Right<B>(val value: B): Either<Nothing, B>(right = value)

    fun getOrThrow() : A =
        left ?: throw NoSuchElementException("No left value, the right value is $right")

    fun getOrNull() = left

    fun exceptionOrNull() = right

    inline fun onSuccess(block: (A) -> Unit) = also {
        if(left != null) block(left)
    }

    inline fun onFailure(block: (B) -> Unit) = also {
        if(right != null) block(right)
    }
}