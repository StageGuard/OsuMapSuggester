package me.stageguard.osu.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class Replay(
    @SerialName("content")
    val content: String,
    @SerialName("encoding")
    val encoding: String
)