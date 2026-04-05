package com.fireflybot.domain.model

enum class TransactionType(val apiValue: String) {
    TRANSFER("transfer"),
    WITHDRAWAL("withdrawal"),
    DEPOSIT("deposit"),
}
