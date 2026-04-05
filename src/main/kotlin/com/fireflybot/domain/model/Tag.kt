package com.fireflybot.domain.model

/**
 * A Firefly III tag.
 * [id] is used as the Telegram callback_data identifier (compact, safe).
 * [name] is the human-readable label shown in buttons and stored in transactions.
 */
data class Tag(val id: String, val name: String)
