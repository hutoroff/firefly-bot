package com.fireflybot.telegram.wizard

import com.fireflybot.firefly.model.Account
import com.fireflybot.firefly.model.Category
import java.time.LocalDateTime

data class WizardSession(
    val chatId: Long,
    val step: WizardStep = WizardStep.SelectType,
    val type: String? = null,
    val sourceAccount: Account? = null,
    val destinationAccount: Account? = null,
    /** Amount for same-currency transfers and withdrawals. */
    val amount: String? = null,
    /** Withdrawal amount in source currency (cross-currency transfers). */
    val sourceAmount: String? = null,
    /** Deposit amount in destination currency (cross-currency transfers). */
    val destAmount: String? = null,
    val dateTime: LocalDateTime = LocalDateTime.now(),
    val tag: String? = null,
    /** Selected category (withdrawal only). */
    val category: Category? = null,
    /** Last expense-account search query; retained so page navigation can re-filter without re-asking. */
    val expenseAccountQuery: String? = null,
    /** Last revenue-account search query; retained so page navigation can re-filter without re-asking. */
    val revenueAccountQuery: String? = null,
    /** Current page for account or category pagination. Reset to 0 at each new selection step. */
    val accountPage: Int = 0,
    /** Message ID of the wizard message to edit in-place. */
    val wizardMessageId: Int? = null,
)
