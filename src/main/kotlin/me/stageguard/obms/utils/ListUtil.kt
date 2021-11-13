package me.stageguard.obms.utils

inline fun <reified T> List<T>.concatenate(vararg others: List<T>): List<T> {
    return listOf(this, *others).flatten()
}
inline fun <reified T> List<T>.concatenate(vararg others: T): List<T> {
    return listOf(*toTypedArray(), *others)
}