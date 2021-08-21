package me.stageguard.obms.osu.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class GetReplayDTO(
    val content: String,
    val encoding: String
)