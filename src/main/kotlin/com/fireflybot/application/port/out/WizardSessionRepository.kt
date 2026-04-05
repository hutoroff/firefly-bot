package com.fireflybot.application.port.out

import com.fireflybot.application.wizard.WizardSession

interface WizardSessionRepository {
    fun get(chatId: Long): WizardSession?
    fun save(session: WizardSession)
    fun delete(chatId: Long)
}
