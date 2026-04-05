package com.fireflybot.telegram.wizard

import java.util.concurrent.ConcurrentHashMap

class WizardSessionStore {
    private val sessions = ConcurrentHashMap<Long, WizardSession>()

    fun get(chatId: Long): WizardSession? = sessions[chatId]

    fun create(chatId: Long): WizardSession {
        val session = WizardSession(chatId = chatId)
        sessions[chatId] = session
        return session
    }

    fun update(session: WizardSession) {
        sessions[session.chatId] = session
    }

    fun remove(chatId: Long) {
        sessions.remove(chatId)
    }
}
