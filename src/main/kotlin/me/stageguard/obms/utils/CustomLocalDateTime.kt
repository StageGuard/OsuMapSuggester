package me.stageguard.obms.utils

import kotlinx.serialization.Serializable
import me.stageguard.obms.osu.api.serializer.ISO8601DateToLocalDateTimeSerializer
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.*

@Serializable(ISO8601DateToLocalDateTimeSerializer::class)
class CustomLocalDateTime private constructor(
    val primitive: LocalDateTime
) {
    override fun toString(): String = primitive.run {
        "$dayOfMonth ${month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} $year ${hour.run { 
            if (this < 10) "0$this" else this.toString()
        }}:${minute.run {
            if (this < 10) "0$this" else this.toString()
        }}"
    }

    companion object {
        fun of(value: LocalDateTime) = CustomLocalDateTime(value)
    }
}