package me.stageguard.obms.bot.route

import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.RouteLock
import me.stageguard.obms.bot.RouteLock.routeLock
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.database.model.WebVerificationStore
import me.stageguard.obms.osu.api.oauth.AuthType
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.message.nextMessage
import net.mamoe.mirai.utils.info
import org.ktorm.dsl.eq
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import java.lang.IllegalStateException

fun GroupMessageSubscribersBuilder.bindAccount() {
    routeLock(startWithIgnoreCase(".bind")) {
        val user = OsuUserInfo.getOsuIdAndName(sender.id)
        if(user == null) {
            val link = OAuthManager.createOAuthLink(AuthType.BIND_ACCOUNT, listOf(sender.id, group.id))
            atReply("请点击这个链接进行 oAuth 授权来绑定你的 osu 账号: $link")
        } else RouteLock.withLockSuspend(sender) {
            atReply("你已经与 osu 账号 ${user.second}(${user.first}) 绑定，要重新绑定吗？\n发送\"是\"或\"确认\"重绑。")
            nextMessage {
                    next -> next.sender.id == this@routeLock.sender.id
            }.contentToString().run {
                if(this == "确认" || this == "是") {
                    val link = OAuthManager.createOAuthLink(AuthType.BIND_ACCOUNT, listOf(sender.id, group.id))
                    atReply("请点击这个链接进行 oAuth 授权来重新绑定你的 osu 账号: $link")
                }
            }
        }
    }

    routeLock(startWithIgnoreCase(".unbind")) {
        Database.query { db ->
            val user = db.sequenceOf(OsuUserInfo).find { it.qq eq sender.id }

            if(user == null) {
                atReply("你并未绑定 osu! 账号。")
            } else RouteLock.withLockSuspend(sender) {
                atReply("""
                你已经与 osu! 账号${user.osuName}(${user.osuId}) 绑定，确认要解除绑定吗？
                解除绑定后将无法使用任何功能。
                发送 "是" 或 "确认" 解除绑定。
            """.trimIndent())
                nextMessage {
                    next -> next.sender.id == this@routeLock.sender.id
                }.contentToString().run {
                    if(this == "确认" || this == "是") {
                        OsuMapSuggester.logger.info { "User unbind: qq ${sender.id} of osu ${user.osuName}(${user.osuId})" }
                        user.delete()
                        atReply("解除绑定成功。")
                    }
                }
            }
        }
    }
}