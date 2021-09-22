package me.stageguard.obms.bot.route

import net.mamoe.mirai.event.Listener
import net.mamoe.mirai.event.MessageSubscribersBuilder
import net.mamoe.mirai.event.events.GroupMessageEvent

fun MessageSubscribersBuilder<GroupMessageEvent, Listener<GroupMessageEvent>, Unit, Unit>.startWithIgnoreCase(
    prefix: String, trim: Boolean = true
) : MessageSubscribersBuilder<GroupMessageEvent, Listener<GroupMessageEvent>, Unit, Unit>.ListeningFilter {
    val toCheck = if (trim) prefix.trimStart() else prefix
    return content { (if (trim) it.trimStart() else it).lowercase().startsWith(toCheck.lowercase()) }
}