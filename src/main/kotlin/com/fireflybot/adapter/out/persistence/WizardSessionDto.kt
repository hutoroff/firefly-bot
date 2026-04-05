package com.fireflybot.adapter.out.persistence

import kotlinx.serialization.Serializable

@Serializable
class WizardSessionDto(
    val chatId: Long,
    /** Simple class name of the WizardStep subtype, e.g. "SelectType". */
    val step: String,
    val transactionType: String? = null,
    val sourceAccountId: String? = null,
    val sourceAccountName: String? = null,
    val sourceAccountCurrencyCode: String? = null,
    val destinationAccountId: String? = null,
    val destinationAccountName: String? = null,
    val destinationAccountCurrencyCode: String? = null,
    val amount: String? = null,
    val sourceAmount: String? = null,
    val destAmount: String? = null,
    /** ISO-8601 string from LocalDateTime.toString(). */
    val dateTime: String,
    val tag: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val expenseAccountQuery: String? = null,
    val revenueAccountQuery: String? = null,
    val accountPage: Int = 0,
    val wizardMessageId: Int? = null,
)
