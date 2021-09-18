package me.stageguard.obms.script.synthetic

abstract class AbstractWrapped<T>(val value: T) : UnwrapSupported<T> {
    override fun unwrap(): T = value
}