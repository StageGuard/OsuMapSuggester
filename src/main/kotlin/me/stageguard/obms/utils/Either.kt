/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE", "unused")

package me.stageguard.obms.utils

/**
 * Safe union of two types.
 */
class Either<out L : Any, out R : Any?> private constructor(
    @PublishedApi
    @JvmField
    internal val value: Any?,
) {
    override fun toString(): String = value.toString()

    companion object {
        ///////////////////////////////////////////////////////////////////////////
        // constructors
        ///////////////////////////////////////////////////////////////////////////

        @PublishedApi
        internal object CheckedTypes

        @PublishedApi
        internal fun <L : Any, R> CheckedTypes.new(value: Any?): Either<L, R> = Either(value)

        @PublishedApi
        internal inline fun <reified L, reified R> checkTypes(value: Any?): CheckedTypes {
            if (!(value is R).xor(value is L)) {
                throw IllegalArgumentException("value(${getTypeHint(value)}) must be either L(${getTypeHint<L>()}) or R(${getTypeHint<R>()}), and must not be both of them.")
            }
            return CheckedTypes
        }

        /**
         * Create a [Either] whose value is [left].
         * @throws IllegalArgumentException if [left] satisfies both types [L] and [R].
         */
        @JvmName("left")
        @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
        @kotlin.internal.LowPriorityInOverloadResolution
        inline operator fun <reified L : Any, reified R> invoke(left: L): Either<L, R> =
            checkTypes<L, R>(left).new(left)

        /**
         * Create a [Either] whose value is [right].
         * @throws IllegalArgumentException if [right] satisfies both types [L] and [R].
         */
        @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
        @kotlin.internal.LowPriorityInOverloadResolution
        @JvmName("right")
        inline operator fun <reified L : Any, reified R> invoke(right: R): Either<L, R> =
            checkTypes<L, R>(right).new(right)


        ///////////////////////////////////////////////////////////////////////////
        // functions
        ///////////////////////////////////////////////////////////////////////////


        inline val <L : Any, reified R> Either<L, R>.right: R get() = value as R
        inline val <L : Any, reified R> Either<L, R>.rightOrNull: R? get() = value as? R
        inline val <L : Any, reified R> Either<L, R>.rightOrThrow: R get() =
            rightOrNull ?: throw IllegalArgumentException("Value cannot be cast to right value, the value is $value")

        inline val <reified L : Any, R> Either<L, R>.left: L get() = value as L
        inline val <reified L : Any, R> Either<L, R>.leftOrNull: L? get() = value as? L
        inline val <reified L : Any, R> Either<L, R>.leftOrThrow: L get() =
            leftOrNull ?: throw IllegalArgumentException("Value cannot be cast to left value, the value is $value")

        inline val <reified L : Any, R> Either<L, R>.isLeft: Boolean get() = value is L
        inline val <L : Any, reified R> Either<L, R>.isRight: Boolean get() = value is R


        inline fun <reified L : Any, reified R, T> Either<L, R>.ifLeft(block: (L) -> T): T? =
            this.leftOrNull?.let(block)

        inline fun <L : Any, reified R, T> Either<L, R>.ifRight(block: (R) -> T): T? =
            this.rightOrNull?.let(block)


        inline fun <reified L : Any, reified R> Either<L, R>.onLeft(block: (L) -> Unit): Either<L, R> {
            this.leftOrNull?.let(block)
            return this
        }

        inline fun <L : Any, reified R> Either<L, R>.onRight(block: (R) -> Unit): Either<L, R> {
            this.rightOrNull?.let(block)
            return this
        }


        inline fun <reified L : Any, reified R, reified T : Any> Either<L, R>.mapLeft(block: (L) -> T): Either<T, R> {
            @Suppress("RemoveExplicitTypeArguments")
            return this.fold(
                onLeft = { invoke<T, R>(block(it)) },
                onRight = { invoke<T, R>(it) }
            )
        }

        inline fun <reified L : Any, reified R, reified T : Any> Either<L, R>.mapRight(block: (R) -> T): Either<L, T> {
            @Suppress("RemoveExplicitTypeArguments")
            return this.fold(
                onLeft = { invoke<L, T>(it) },
                onRight = { invoke<L, T>(it.let(block)) }
            )
        }


        inline fun <reified L : Any, reified R, T> Either<L, R>.fold(
            onLeft: (L) -> T,
            onRight: (R) -> T,
        ): T = leftOrNull?.let { onLeft(it) } ?: (value as R).let(onRight)

        inline fun <reified T> Either<Throwable, T>.toResult(): Result<T> = this.fold(
            onLeft = { Result.failure(it) },
            onRight = { Result.success(it) }
        )

        @PublishedApi
        internal fun getTypeHint(value: Any?): String {
            return if (value == null) "null"
            else value::class.run { simpleName ?: toString() }
        }

        @PublishedApi
        internal inline fun <reified T> getTypeHint(): String {
            return T::class.run { simpleName ?: toString() }
        }
    }
}