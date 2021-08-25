package me.stageguard.obms.osu.api.serializer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.stageguard.obms.utils.CustomLocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

@OptIn(ExperimentalSerializationApi::class)
@Serializer(CustomLocalDateTime::class)
class ISO8601DateToLocalDateTimeSerializer : KSerializer<CustomLocalDateTime> {
    private val stringSerializer = String.serializer()

    override val descriptor: SerialDescriptor get() =
        PrimitiveSerialDescriptor(
            "me.stageguard.obms.ISO8601DateToLocalDateTimeSerializer",
            stringSerializer.descriptor.kind as PrimitiveKind
        )

    override fun serialize(encoder: Encoder, value: CustomLocalDateTime) =
        stringSerializer.serialize(encoder, value.let {
            it.primitive.atZone(ZoneId.systemDefault()).run {
                it.primitive.toString() + offset.toString()
            }
        })

    override fun deserialize(decoder: Decoder): CustomLocalDateTime =
        stringSerializer.deserialize(decoder).let {
            CustomLocalDateTime.of(OffsetDateTime.parse(it).toLocalDateTime())
        }

}