package me.stageguard.obms.bot.route

import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.database.model.OsuUserInfo
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.message.nextMessage

val DEVELOPING_DEBUG = System.getProperty("me.stageguard.obms.developing_debug", false.toString()).toBoolean()
fun GroupMessageSubscribersBuilder.bindAccount() {
    startsWith(".bind") {
        if(false) {
            atReply("Bind account is unavailable in debug mode.")
            return@startsWith
        }
        val user = OsuUserInfo.getOsuIdAndName(sender.id)
        if(user == null) {
            val link = OAuthManager.createOAuthLink(sender.id, group.id)
            atReply("请点击这个链接进行 oAuth 授权来绑定你的 osu 账号: $link")
        } else {
            atReply("你已经与 osu 账号 ${user.second}(${user.first}) 绑定，要重新绑定吗？\n发送\"是\"或\"确认\"重绑。")
            nextMessage {
                next -> next.sender.id == this@startsWith.sender.id
            }.contentToString().run {
                if(this == "确认" || this == "是") {
                    val link = OAuthManager.createOAuthLink(sender.id, group.id)
                    atReply("请点击这个链接进行 oAuth 授权来重新绑定你的 osu 账号: $link")
                }
            }
        }
    }
}