package com.fireflybot.domain.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dtFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

sealed class Transaction {
    abstract val dateTime: LocalDateTime
    abstract val tag: String?

    data class Transfer(
        val sourceAccount: Account,
        val destinationAccount: Account,
        /** Amount in a shared currency; null when the accounts have different currencies. */
        val amount: String?,
        /** Withdrawal leg for cross-currency transfers (source currency). */
        val sourceAmount: String?,
        /** Deposit leg for cross-currency transfers (destination currency). */
        val destAmount: String?,
        override val dateTime: LocalDateTime,
        override val tag: String?,
    ) : Transaction()

    data class Withdrawal(
        val sourceAccount: Account,
        val expenseAccount: Account,
        val amount: String,
        val category: Category?,
        override val dateTime: LocalDateTime,
        override val tag: String?,
    ) : Transaction()

    data class Deposit(
        val revenueAccount: Account,
        val destinationAccount: Account,
        val amount: String,
        val category: Category?,
        override val dateTime: LocalDateTime,
        override val tag: String?,
    ) : Transaction()

    fun previewText(): String = buildString {
        appendLine("Transaction Preview")
        when (this@Transaction) {
            is Transfer -> {
                val amountLine = when {
                    amount != null -> "$amount ${sourceAccount.currencyCode}"
                    sourceAmount != null && destAmount != null ->
                        "$sourceAmount ${sourceAccount.currencyCode} -> $destAmount ${destinationAccount.currencyCode}"
                    else -> "-"
                }
                appendLine("Type: Transfer")
                appendLine("From: ${sourceAccount.name} (${sourceAccount.currencyCode})")
                appendLine("To: ${destinationAccount.name} (${destinationAccount.currencyCode})")
                appendLine("Amount: $amountLine")
            }
            is Withdrawal -> {
                appendLine("Type: Withdrawal")
                appendLine("From: ${sourceAccount.name} (${sourceAccount.currencyCode})")
                appendLine("To: ${expenseAccount.name}")
                appendLine("Amount: $amount ${sourceAccount.currencyCode}")
                appendLine("Category: ${category?.name ?: "-"}")
            }
            is Deposit -> {
                appendLine("Type: Deposit")
                appendLine("From: ${revenueAccount.name}")
                appendLine("To: ${destinationAccount.name} (${destinationAccount.currencyCode})")
                appendLine("Amount: $amount ${destinationAccount.currencyCode}")
                appendLine("Category: ${category?.name ?: "-"}")
            }
        }
        appendLine("Date: ${dateTime.format(dtFormatter)}")
        append("Tag: ${tag ?: "(none)"}")
    }

    fun successText(): String = buildString {
        when (this@Transaction) {
            is Transfer -> {
                appendLine("Transfer submitted successfully!")
                appendLine("From: ${sourceAccount.name} -> ${destinationAccount.name}")
                val amountLine = amount
                    ?: "$sourceAmount ${sourceAccount.currencyCode} / $destAmount ${destinationAccount.currencyCode}"
                appendLine("Amount: $amountLine")
            }
            is Withdrawal -> {
                appendLine("Withdrawal submitted successfully!")
                appendLine("From: ${sourceAccount.name}")
                appendLine("To: ${expenseAccount.name}")
                appendLine("Amount: $amount ${sourceAccount.currencyCode}")
                appendLine("Category: ${category?.name ?: "-"}")
            }
            is Deposit -> {
                appendLine("Deposit submitted successfully!")
                appendLine("From: ${revenueAccount.name}")
                appendLine("To: ${destinationAccount.name}")
                appendLine("Amount: $amount ${destinationAccount.currencyCode}")
                appendLine("Category: ${category?.name ?: "-"}")
            }
        }
        appendLine("Date: ${dateTime.format(dtFormatter)}")
        append("Tag: ${tag ?: "(none)"}")
    }
}
