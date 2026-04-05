package com.fireflybot.application.port.`in`

import com.fireflybot.application.wizard.WizardResult

interface WizardUseCase {
    /** Creates a new session. Call [setWizardMessageId] after sending the initial message. */
    fun startNewWizard(chatId: Long)
    /** Stores the Telegram message ID that the wizard edits in-place. */
    fun setWizardMessageId(chatId: Long, messageId: Int)
    /** Returns the stored wizard message ID for the given chat, or null if no active session. */
    fun getWizardMessageId(chatId: Long): Int?
    fun handleCallback(chatId: Long, messageId: Int, data: String): WizardResult
    fun handleText(chatId: Long, text: String): WizardResult
}
