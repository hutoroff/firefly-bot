package com.fireflybot.telegram

import com.fireflybot.config.AppConfig
import com.fireflybot.firefly.FireflyClient
import com.fireflybot.telegram.wizard.WizardHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

class FireflyBot(
    private val config: AppConfig,
    @Suppress("UnusedPrivateProperty")
    private val fireflyClient: FireflyClient,
    private val wizardHandler: WizardHandler,
) : TelegramLongPollingBot(config.telegramToken) {

    private val log = KotlinLogging.logger {}

    override fun getBotUsername(): String = config.telegramBotUsername

    override fun onUpdateReceived(update: Update) {
        when {
            update.hasCallbackQuery() -> {
                val query = update.callbackQuery
                val userId = query.from?.id
                val chatId = query.message?.chatId
                log.info { "Received callback from user=$userId chat=$chatId" }
                wizardHandler.handleCallback(query, this)
            }
            update.hasMessage() -> handleMessage(update.message)
        }
    }

    private fun handleMessage(message: Message) {
        val userId = message.from?.id
        val chatId = message.chatId
        log.info { "Received message from user=$userId chat=$chatId" }

        val text = message.text ?: return
        when {
            text == "/start" -> handleStart(chatId)
            text == "/new" -> wizardHandler.handleNew(chatId, this)
            !text.startsWith("/") -> wizardHandler.handleText(message, this)
        }
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
}
