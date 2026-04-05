package com.fireflybot.config

data class AppConfig(
    val telegramToken: String,
    val telegramBotUsername: String,
    val telegramAllowedUserId: Long,
    val fireflyHost: String,
    val fireflyToken: String,
) {
    companion object {
        fun fromEnv(): AppConfig = AppConfig(
            telegramToken = requireEnv("TELEGRAM_BOT_TOKEN"),
            telegramBotUsername = requireEnv("TELEGRAM_BOT_USERNAME"),
            telegramAllowedUserId = requireEnv("TELEGRAM_ALLOWED_USER_ID").toLongOrNull()
                ?: error("Required environment variable 'TELEGRAM_ALLOWED_USER_ID' must be a valid Telegram user id"),
            fireflyHost = requireEnv("FIREFLY_HOST"),
            fireflyToken = requireEnv("FIREFLY_TOKEN"),
        )

        private fun requireEnv(name: String): String =
            System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
                ?: error("Required environment variable '$name' is not set or is blank")
    }
}
