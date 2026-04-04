package com.fireflybot.firefly.model

import kotlinx.serialization.Serializable

@Serializable
data class TransactionSingle(
    val data: TransactionRead,
)

@Serializable
data class TransactionRead(
    val id: String,
    val type: String,
)
