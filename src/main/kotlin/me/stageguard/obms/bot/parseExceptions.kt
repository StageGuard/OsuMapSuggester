package me.stageguard.obms.bot

import me.stageguard.obms.OsuMapSuggester

fun parseExceptions(ex: Exception) : String {
    OsuMapSuggester.logger.error(ex)
    val message = ex.message ?: "NO_MESSAGE"
    return when {
        message.contains("NOT_BIND") -> "未绑定账号，请输入 .bind 进行绑定。"
        message.contains("\"authentication\":\"basic\"") -> "绑定失效，请输入 .bind 重新绑定以更新令牌。"
        message.contains("SCORE_LIST_EMPTY") -> "查询结果为空。"
        message.contains("NO_RECENT_SCORE") -> "没有最近成绩。"
        message.contains("401") -> "绑定失效，请输入 .bind 重新绑定以更新令牌。"
        message.contains("INVALID_BP_ORD") -> "所查询的 BP 不在 1-100 范围内"
        message.contains("INVALID_INPUT_FORMAT") -> "输入数据格式有误"
        message.contains("FAILED_AFTER_N_TRIES") ->
            message.substringAfterLast("FAILED_AFTER_N_TRIES:", message)
        message.contains("JS_COMPILE_ERROR") -> "JavaScript 编译错误：$ex"
        message.contains("JS_RUNTIME_ERROR") -> "JavaScript 运行错误：$ex"
        message.contains("TIMEOUT") -> "请求 osu!api 超时，请重试。"
        message.contains("BEATMAP_PARSE_ERROR") ->
            "谱面解析异常：${message.substringAfter("BEATMAP_PARSE_ERROR:")}"
        else -> "unhandled exception: $message"
    }
}