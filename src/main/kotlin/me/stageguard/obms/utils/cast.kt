package me.stageguard.obms.utils

import kotlin.contracts.contract

inline fun <reified R> Any.cast(): R {
    contract {
        returns() implies (this@cast is R)
    }
    return this as R
}