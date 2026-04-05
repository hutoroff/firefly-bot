package com.fireflybot.adapter.`in`.telegram

import com.fireflybot.application.port.`in`.WizardUseCase
import com.fireflybot.config.AppConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

class FireflyBot(
    private val config: AppConfig,
    private val wizardUseCase: WizardUseCase,
    private val presenter: TelegramWizardPresenter,
) : TelegramLongPollingBot(config.telegramToken) {

    private val log = KotlinLogging.logger {}

    override fun getBotUsername(): String = config.telegramBotUsername

    override fun onUpdateReceived(update: Update) {
        val userId = when {
            update.hasCallbackQuery() -> update.callbackQuery.from?.id
            update.hasMessage() -> update.message.from?.id
            else -> null
        }
        if (userId != config.telegramAllowedUserId) {
            log.warn { "Ignored update from unauthorized user=$userId" }
            if (update.hasCallbackQuery()) answerCallback(update.callbackQuery.id)
            return
        }
        when {
            update.hasCallbackQuery() -> {
                val query = update.callbackQuery
                val chatId = query.message.chatId
                val messageId = query.message.messageId
                log.info { "Received callback from user=${query.from?.id} chat=$chatId" }
                answerCallback(query.id)
                val result = wizardUseCase.handleCallback(chatId, messageId, query.data ?: return)
                presenter.render(this, chatId, messageId, result)
            }
            update.hasMessage() -> handleMessage(update.message)
        }
    }

    private fun handleMessage(message: Message) {
        val chatId = message.chatId
        log.info { "Received message from user=${message.from?.id} chat=$chatId" }
        val text = message.text ?: return
        when {
            text == "/start" -> handleStart(chatId)
            text == "/new" -> handleNew(chatId)
            !text.startsWith("/") -> {
                val wizardMsgId = wizardUseCase.getWizardMessageId(chatId)
                val result = wizardUseCase.handleText(chatId, text)
                presenter.render(this, chatId, wizardMsgId, result)
            }
        }
    }

    private fun handleNew(chatId: Long) {
        wizardUseCase.startNewWizard(chatId)
        val msg = presenter.renderNewWizard(this, chatId) ?: return
        wizardUseCase.setWizardMessageId(chatId, msg.messageId)
    }

    private fun handleStart(chatId: Long) {
        try {
            execute(
                SendMessage.builder()
                    .chatId(chatId)
                    .text("Welcome to Firefly Bot! Use /new to create a transaction.")
                    .build()
            )
        } catch (e: TelegramApiException) {
            log.error(e) { "Failed to send /start reply to chat=$chatId" }
        }
    }

    private fun answerCallback(callbackId: String) {
        try {
            execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build())
        } catch (e: TelegramApiException) {
            log.error(e) { "Failed to answer callback $callbackId" }
        }
    }
}
