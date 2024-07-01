package me.stageguard.obms.bot.route

import com.mikuac.shiro.annotation.GroupMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import me.stageguard.obms.bot.MessageRoute.atReplyText
import me.stageguard.obms.bot.MessageRoute.routeLock
import org.springframework.stereotype.Component

@Shiro
@Component
open class Help {
    @GroupMessageHandler
    @MessageHandlerFilter(startWith = [".help", "。help"])
    fun trigger(event: GroupMessageEvent) = event.routeLock {
        atReplyText("See: https://github.com/StageGuard/OsuMapSuggester/wiki")
    }
}