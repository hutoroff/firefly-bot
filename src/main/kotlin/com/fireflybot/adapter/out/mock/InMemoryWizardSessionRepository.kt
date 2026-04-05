package com.fireflybot.adapter.out.mock

import com.fireflybot.application.port.out.WizardSessionRepository
import com.fireflybot.application.wizard.WizardSession
import java.util.concurrent.ConcurrentHashMap

class InMemoryWizardSessionRepository : WizardSessionRepository {

    private val sessions = ConcurrentHashMap<Long, WizardSession>()

    override fun get(chatId: Long): WizardSession? = sessions[chatId]

    override fun save(session: WizardSession) {
        sessions[session.chatId] = session
    }

    override fun delete(chatId: Long) {
        sessions.remove(chatId)
    }
}
