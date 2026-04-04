package com.fireflybot.config

data class AppConfig(
    val telegramToken: String,
    val telegramBotUsername: String,
    val fireflyHost: String,
    val fireflyToken: String,
) {
    companion object {
        fun fromEnv(): AppConfig = AppConfig(
            telegramToken = requireEnv("TELEGRAM_BOT_TOKEN"),
            telegramBotUsername = requireEnv("TELEGRAM_BOT_USERNAME"),
            fireflyHost = requireEnv("FIREFLY_HOST"),
            fireflyToken = requireEnv("FIREFLY_TOKEN"),
        )

        private fun requireEnv(name: String): String =
            System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
                ?: error("Required environment variable '$name' is not set or is blank")
    }
}
