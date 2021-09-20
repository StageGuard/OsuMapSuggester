package me.stageguard.obms.bot

import net.mamoe.mirai.contact.UserOrBot
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.event.Listener
import net.mamoe.mirai.event.MessageSubscribersBuilder
import net.mamoe.mirai.event.events.GroupMessageEvent

object RouteLock {
    private val triggerCache = mutableSetOf<Long>()

    fun GroupMessageSubscribersBuilder.routeLock(
        other: MessageSubscribersBuilder<GroupMessageEvent, Listener<GroupMessageEvent>, Unit, Unit>.ListeningFilter,
        block: suspend GroupMessageEvent.(String) -> Unit
    ) = (newListeningFilter { sender.id !in triggerCache } and other).invoke { block(this, it) }

    fun release(sender: UserOrBot) {
        triggerCache.removeIf { it == sender.id }
    }
    fun add(sender: UserOrBot) {
        triggerCache.add(sender.id)
    }

    suspend fun withLockSuspend(sender: UserOrBot, block: suspend () -> Unit) {
        add(sender)
        block()
        release(sender)
    }

    fun withLock(sender: UserOrBot, block: () -> Unit) {
        add(sender)
        block()
        release(sender)
    }
}