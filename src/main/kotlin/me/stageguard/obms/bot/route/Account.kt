package me.stageguard.obms.bot.route

import com.mikuac.shiro.annotation.GroupMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import jakarta.annotation.Resource
import kotlinx.coroutines.launch
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.bot.MessageRoute.atReplyText
import me.stageguard.obms.bot.MessageRoute.routeLock
import me.stageguard.obms.bot.refactoredExceptionCatcher
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.OsuUserInfoEx
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.osu.api.oauth.AuthType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Shiro
@Component
open class Account {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Resource
    private lateinit var oAuthManager: OAuthManager
    @Resource
    private lateinit var database: Database
    @Resource
    private lateinit var osuUserInfoEx: OsuUserInfoEx

    @GroupMessageHandler
    @MessageHandlerFilter(startWith = [".bind", "。bind"])
    fun bind(event: GroupMessageEvent) = event.routeLock {
        OsuMapSuggester.scope.launch(refactoredExceptionCatcher) {
            val user = osuUserInfoEx.getOsuIdAndName(sender.userId)
            if(user == null) {
                val link = oAuthManager.createOAuthLink(AuthType.BIND_ACCOUNT, listOf(sender.userId, groupId))
                atReplyText("请点击这个链接进行 oAuth 授权来绑定你的 osu 账号: $link")
            } else {
                atReplyText("你已经与 osu 账号 ${user.second}(${user.first}) 绑定，若需重新绑定请输入 .rebind 指令。")
            }
        }
    }

    @GroupMessageHandler
    @MessageHandlerFilter(startWith = [".rebind", "。rebind"])
    fun rebind(event: GroupMessageEvent) = event.routeLock {
        OsuMapSuggester.scope.launch {
            val link = oAuthManager.createOAuthLink(AuthType.BIND_ACCOUNT, listOf(sender.userId, groupId))
            atReplyText("请点击这个链接进行 oAuth 授权来重新绑定你的 osu 账号: $link")
        }
    }

    @GroupMessageHandler
    @MessageHandlerFilter(startWith = [".rot", "。rot"])
    fun refreshOsuToken(event: GroupMessageEvent) = event.routeLock {
        OsuMapSuggester.scope.launch {
            val user = osuUserInfoEx.getOsuIdAndName(sender.userId)
            if (user == null) {
                atReplyText("请首先输入 .bind 绑定账号，在您的绑定失效后才能使用此指令更新令牌。");
                return@launch
            }
            val link = oAuthManager.createOAuthLink(AuthType.BIND_ACCOUNT, listOf(sender.userId, groupId))
            atReplyText("请点击这个链接进行绑定更新: $link")
        }
    }

    /*@GroupMessageHandler
    @MessageHandlerFilter(startWith = [".rebind", "。rebind"])
    fun refreshOsuToken(event: GroupMessageEvent) = event.routeLock {
        OsuMapSuggester.scope.launch {
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
    }*/


}