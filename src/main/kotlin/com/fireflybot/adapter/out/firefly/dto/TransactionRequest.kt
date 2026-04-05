package com.fireflybot.adapter.out.firefly.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StoreTransactionRequest(
    @SerialName("error_if_duplicate_hash") val errorIfDuplicateHash: Boolean = false,
    @SerialName("apply_rules") val applyRules: Boolean = true,
    @SerialName("fire_webhooks") val fireWebhooks: Boolean = true,
    val transactions: List<TransactionSplit>,
)

@Serializable
data class TransactionSplit(
    /** "withdrawal", "deposit", or "transfer" */
    val type: String,
    /** ISO 8601 date, e.g. "2024-01-15T10:30:00+00:00" */
    val date: String,
    val amount: String,
    val description: String,
    @SerialName("currency_code") val currencyCode: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("destination_id") val destinationId: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("destination_name") val destinationName: String? = null,
    val category: String? = null,
    val notes: String? = null,
    val tags: List<String>? = null,
)
