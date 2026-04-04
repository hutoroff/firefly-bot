package com.fireflybot.telegram

import com.fireflybot.config.AppConfig
import com.fireflybot.firefly.FireflyClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

class FireflyBot(
    private val config: AppConfig,
    @Suppress("UnusedPrivateProperty")
    private val fireflyClient: FireflyClient,
) : TelegramLongPollingBot(config.telegramToken) {

    private val log = KotlinLogging.logger {}

    override fun getBotUsername(): String = config.telegramBotUsername

    override fun onUpdateReceived(update: Update) {
        val message = update.message ?: return
        val userId = message.from?.id
        val chatId = message.chatId

        log.info { "Received update from user=$userId chat=$chatId" }

        when (message.text) {
            "/start" -> handleStart(chatId)
            // TODO: add transaction creation commands
        }
    }

    private fun handleStart(chatId: Long) {
        try {
            execute(
                SendMessage.builder()
                    .chatId(chatId)
                    .text("Firefly Bot is running. Transaction creation is coming soon.")
                    .build()
            )
        } catch (e: TelegramApiException) {
            log.error(e) { "Failed to send /start reply to chat=$chatId" }
        }
    }
}
