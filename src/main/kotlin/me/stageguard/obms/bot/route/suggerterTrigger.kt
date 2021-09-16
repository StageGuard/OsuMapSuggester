package me.stageguard.obms.bot.route

import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.parseExceptions
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.utils.Either
import me.stageguard.sctimetable.utils.interactiveConversation
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder

fun GroupMessageSubscribersBuilder.suggesterTrigger() {
    finding(Regex("[来|搞]一?[张|点](.+)(?:[谱|铺]面?|图)")) { mr ->
        try {
            val variant = mr.groupValues[1]
            val userId = OsuUserInfo.getOsuId(sender.id) ?: throw IllegalStateException("NOT_BIND")

        } catch (ex: Exception) {
            atReply("获取谱面推荐时发生了错误：${parseExceptions(ex)}")
        }
    }

    startsWith(".addRuleset") {
        try {
            OsuUserInfo.getOsuId(sender.id) ?: throw IllegalStateException("NOT_BIND")

            interactiveConversation {
                send("")
            }
        } catch (ex: Exception) {
            atReply("添加谱面类型规则时发生了错误：${parseExceptions(ex)}")
        }
    }
}