package com.fireflybot

import com.fireflybot.di.appModule
import com.fireflybot.telegram.FireflyBot
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.context.startKoin
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

private val log = KotlinLogging.logger {}

fun main() {
    log.info { "Starting Firefly Bot..." }

    val koin = startKoin {
        printLogger()
        modules(appModule)
    }.koin

    try {
        val bot = koin.get<FireflyBot>()
        val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
        botsApi.registerBot(bot)
        log.info { "Firefly Bot started successfully" }
    } catch (e: Exception) {
        log.error(e) { "Failed to start Firefly Bot" }
        throw e
    }
}
