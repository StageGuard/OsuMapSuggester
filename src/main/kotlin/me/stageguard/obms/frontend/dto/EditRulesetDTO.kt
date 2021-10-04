package me.stageguard.obms.frontend.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebVerificationResponseDTO(
    //  0: success
    //  1: cookie not found
    //  2: not bind a qq -> 用户没有绑定过 QQ 而验证(qq = -1)
    //  3: unbound -> 用户之前解绑了而验证
    // -1: internal error
    val result: Int,
    val qq: Long = -1,
    val osuId: Int = -1,
    val osuName: String = "",
    val errorMessage: String = ""
)

@Serializable
data class WebVerificationRequestDTO(
    val token: String
)

@Serializable
data class CheckEditAccessRequestDTO(
    val qq: Long,
    // 1: create a new ruleset
    // 2: edit a existing ruleset
    val editType: Int,
    // equal to 0 when `editType` = 1
    // or else equal to ruleset id
    val rulesetId: Int
)

@Serializable
data class CheckEditAccessResponseDTO(
    //  0: have permission
    //  1: ruleset not found
    //  2: not the ruleset creator
    // -1: internal error
    val result: Int,
    val ruleset: EditRulesetDTO = EditRulesetDTO(),
    val errorMessage: String = ""
)

@Serializable
data class EditRulesetDTO(
    val id: Int = 0,
    val name: String = "",
    val triggers: List<String> = listOf(),
    val condition: String = ""
)

@Serializable
data class CreateVerificationLinkRequestDTO(
    @SerialName("callback")
    val callbackPath: String
)

@Serializable
data class CreateVerificationLinkResponseDTO(
    //  0: success
    // -1: internal error
    val result: Int,
    val link: String = "",
    val errorMessage: String = ""
)

@Serializable
data class CheckSyntaxRequestDTO(
    val code: String
)

@Serializable
data class CheckSyntaxResponseDTO(
    //  0: success
    // -1: internal error
    val result: Int,
    val haveSyntaxError: Boolean = false,
    val message: List<String> = listOf(),
    val errorMessage: String = ""
)

@Serializable
data class SubmitRequestDTO(
    val token: String,
    // ruleset.id 为 -1 时为创建新的 ruleset
    val ruleset: EditRulesetDTO
)

@Serializable
data class SubmitResponseDTO(
    //  0: success
    //  1: authorization failed
    //  2: illegal access
    //  3: illegal parameter
    // -1: internal error
    val result: Int,
    val errorMessage: String = ""
)