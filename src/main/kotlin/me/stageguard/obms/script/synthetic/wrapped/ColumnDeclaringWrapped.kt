package me.stageguard.obms.script.synthetic.wrapped

import me.stageguard.obms.script.synthetic.AbstractWrapped
import org.ktorm.dsl.*
import org.ktorm.schema.ColumnDeclaring

class ColumnDeclaringComparableNumberWrapped<N>(c: ColumnDeclaring<N>) : ColumnDeclaringWrapped<N>(c)
        where N : Number, N : Comparable<N>
{
    fun plus(other: N) = ColumnDeclaringComparableNumberWrapped(value.plus(other))
    fun plus(expr: ColumnDeclaringComparableNumberWrapped<N>) =
        ColumnDeclaringComparableNumberWrapped(value.plus(expr.unwrap()))

    fun minus(other: N) = ColumnDeclaringComparableNumberWrapped(value.minus(other))
    fun minus(expr: ColumnDeclaringComparableNumberWrapped<N>) =
        ColumnDeclaringComparableNumberWrapped(value.minus(expr.unwrap()))

    fun times(other: N) = ColumnDeclaringComparableNumberWrapped(value.times(other))
    fun times(expr: ColumnDeclaringComparableNumberWrapped<N>) =
        ColumnDeclaringComparableNumberWrapped(value.times(expr.unwrap()))

    fun div(other: N) = ColumnDeclaringComparableNumberWrapped(value.div(other))
    fun div(expr: ColumnDeclaringComparableNumberWrapped<N>) =
        ColumnDeclaringComparableNumberWrapped(value.div(expr.unwrap()))

    fun rem(other: N) = ColumnDeclaringComparableNumberWrapped(value.rem(other))
    fun rem(expr: ColumnDeclaringComparableNumberWrapped<N>) =
        ColumnDeclaringComparableNumberWrapped(value.rem(expr.unwrap()))

    fun less(other: N) = ColumnDeclaringBooleanWrapped(value.less(other))
    fun less(expr: ColumnDeclaringComparableNumberWrapped<N>) =
        ColumnDeclaringBooleanWrapped(value.less(expr.unwrap()))

    fun lessEq(other: N) = ColumnDeclaringBooleanWrapped(value.lessEq(other))
    fun lessEq(expr: ColumnDeclaringComparableNumberWrapped<N>) =
        ColumnDeclaringBooleanWrapped(value.lessEq(expr.unwrap()))

    fun greater(other: N) = ColumnDeclaringBooleanWrapped(value.greater(other))
    fun greater(expr: ColumnDeclaringComparableNumberWrapped<N>) =
        ColumnDeclaringBooleanWrapped(value.greater(expr.unwrap()))

    fun greaterEq(other: N) = ColumnDeclaringBooleanWrapped(value.greaterEq(other))
    fun greaterEq(expr: ColumnDeclaringComparableNumberWrapped<N>) =
        ColumnDeclaringBooleanWrapped(value.greaterEq(expr.unwrap()))

    override fun notEq(other: N) = ColumnDeclaringBooleanWrapped(value.notEq(other))
    override fun notEq(expr: ColumnDeclaringWrapped<N>) = ColumnDeclaringBooleanWrapped(value.notEq(expr.unwrap()))
}

class ColumnDeclaringBooleanWrapped(c: ColumnDeclaring<Boolean>) : ColumnDeclaringWrapped<Boolean>(c) {
    fun and(other: Boolean) = ColumnDeclaringBooleanWrapped(value.and(other))
    fun and(expr: ColumnDeclaringBooleanWrapped) = ColumnDeclaringBooleanWrapped(value.and(expr.unwrap()))

    fun or(other: Boolean) = ColumnDeclaringBooleanWrapped(value.or(other))
    fun or(expr: ColumnDeclaringBooleanWrapped) = ColumnDeclaringBooleanWrapped(value.or(expr.unwrap()))

    fun xor(other: Boolean) = ColumnDeclaringBooleanWrapped(value.xor(other))
    fun xor(expr: ColumnDeclaringBooleanWrapped) = ColumnDeclaringBooleanWrapped(value.xor(expr.unwrap()))
}

open class ColumnDeclaringWrapped<T : Any>(c: ColumnDeclaring<T>) : AbstractWrapped<ColumnDeclaring<T>>(c) {
    fun eq(other: T) = ColumnDeclaringBooleanWrapped(value.eq(other))
    fun eq(expr: ColumnDeclaringWrapped<T>) = ColumnDeclaringBooleanWrapped(value.eq(expr.unwrap()))

    fun like(other: String) = ColumnDeclaringBooleanWrapped(value.like(other))
    fun like(expr: ColumnDeclaringWrapped<String>) = ColumnDeclaringBooleanWrapped(value.like(expr.unwrap()))

    open fun notEq(other: T) = ColumnDeclaringBooleanWrapped(value.notEq(other))
    open fun notEq(expr: ColumnDeclaringWrapped<T>) = ColumnDeclaringBooleanWrapped(value.notEq(expr.unwrap()))

    fun notLike(other: String) = ColumnDeclaringBooleanWrapped(value.notLike(other))
    fun notLike(expr: ColumnDeclaringWrapped<String>) = ColumnDeclaringBooleanWrapped(value.notLike(expr.unwrap()))
}