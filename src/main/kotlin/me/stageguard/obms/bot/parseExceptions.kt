package me.stageguard.obms.bot

import me.stageguard.obms.OsuMapSuggester

fun parseExceptions(ex: Exception) : String {
    OsuMapSuggester.logger.error(ex)
    val message = ex.message ?: "NO_MESSAGE"
    return when {
        message.contains("NOT_BIND") -> "未绑定账号，请输入 .bind 进行绑定。"
        message.contains("\"authentication\":\"basic\"") ->
            "绑定失效，请输入 .bind 重新绑定以更新令牌。"
        message.contains("SCORE_LIST_EMPTY") -> "查询结果为空。"
        message.contains("NO_RECENT_SCORE") -> "没有最近成绩。"
        message.contains("FAILED_AFTER_N_TRIES") ->
            message.substringAfterLast("FAILED_AFTER_N_TRIES:", message)
        message.contains("401") ->
            "绑定失效，请输入 .bind 重新绑定以更新令牌。"
        else -> "unhandled exception: $message"
    }
}