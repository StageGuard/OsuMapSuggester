package me.stageguard.osu.api.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@PublishedApi
internal object OffsetDateTimeSerializer: KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = this::class.qualifiedName!!,
        kind = PrimitiveKind.STRING
    )
    private val formatter: DateTimeFormatter by lazy {
        val pattern  = System.getProperty("me.stageguard.osu.api.dto.datetime.formatter")
        if (pattern != null) {
            DateTimeFormatter.ofPattern(pattern)
        } else {
            DateTimeFormatter.ISO_OFFSET_DATE_TIME
        }
    }

    override fun deserialize(decoder: Decoder): OffsetDateTime {
        val text = decoder.decodeString()
        return OffsetDateTime.parse(text, formatter)
    }

    override fun serialize(encoder: Encoder, value: OffsetDateTime) {
        val text = value.format(formatter)
        encoder.encodeString(text)
    }
}