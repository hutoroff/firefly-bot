package com.fireflybot.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class TransactionTest {

    private val dt = LocalDateTime.of(2024, 3, 15, 10, 30)
    private val usdAccount = Account("1", "Checking", "USD")
    private val savingsAccount = Account("2", "Savings", "USD")
    private val eurAccount = Account("3", "Euro Account", "EUR")
    private val expenseAccount = Account("e1", "Grocery Store", "USD")
    private val revenueAccount = Account("r1", "Employer Paycheck", "USD")
    private val category = Category("cat1", "Food & Dining")

    // ── Transfer previewText ───────────────────────────────────────────────────

    @Test
    fun `previewText same-currency transfer shows single amount in source currency`() {
        val tx = Transaction.Transfer(
            sourceAccount = usdAccount,
            destinationAccount = savingsAccount,
            amount = "100.00",
            sourceAmount = null,
            destAmount = null,
            dateTime = dt,
            tag = null,
        )
        val text = tx.previewText()
        assertTrue(text.contains("Type: Transfer"))
        assertTrue(text.contains("From: Checking (USD)"))
        assertTrue(text.contains("To: Savings (USD)"))
        assertTrue(text.contains("Amount: 100.00 USD"))
        assertTrue(text.contains("Date: 15.03.2024 10:30"))
        assertTrue(text.contains("Tag: (none)"))
        assertTrue(text.contains("Description: (none)"))
    }

    @Test
    fun `previewText cross-currency transfer shows both amounts with arrow`() {
        val tx = Transaction.Transfer(
            sourceAccount = usdAccount,
            destinationAccount = eurAccount,
            amount = null,
            sourceAmount = "100.00",
            destAmount = "92.00",
            dateTime = dt,
            tag = "travel",
        )
        val text = tx.previewText()
        assertTrue(text.contains("100.00 USD -> 92.00 EUR"))
        assertTrue(text.contains("Tag: travel"))
        assertTrue(text.contains("Description: (none)"))
    }

    @Test
    fun `previewText transfer with no amounts shows dash`() {
        val tx = Transaction.Transfer(
            sourceAccount = usdAccount,
            destinationAccount = savingsAccount,
            amount = null,
            sourceAmount = null,
            destAmount = null,
            dateTime = dt,
            tag = null,
        )
        val text = tx.previewText()
        assertTrue(text.contains("Amount: -"))
    }

    // ── Withdrawal previewText ────────────────────────────────────────────────

    @Test
    fun `previewText withdrawal with category and tag`() {
        val tx = Transaction.Withdrawal(
            sourceAccount = usdAccount,
            expenseAccount = expenseAccount,
            amount = "50.00",
            category = category,
            dateTime = dt,
            tag = "food",
        )
        val text = tx.previewText()
        assertTrue(text.contains("Type: Withdrawal"))
        assertTrue(text.contains("From: Checking (USD)"))
        assertTrue(text.contains("To: Grocery Store"))
        assertTrue(text.contains("Amount: 50.00 USD"))
        assertTrue(text.contains("Category: Food & Dining"))
        assertTrue(text.contains("Tag: food"))
        assertTrue(text.contains("Description: (none)"))
    }

    @Test
    fun `previewText withdrawal without category shows dash`() {
        val tx = Transaction.Withdrawal(
            sourceAccount = usdAccount,
            expenseAccount = expenseAccount,
            amount = "25.00",
            category = null,
            dateTime = dt,
            tag = null,
        )
        val text = tx.previewText()
        assertTrue(text.contains("Category: -"))
        assertTrue(text.contains("Tag: (none)"))
        assertTrue(text.contains("Description: (none)"))
    }

    // ── Deposit previewText ───────────────────────────────────────────────────

    @Test
    fun `previewText deposit with category`() {
        val tx = Transaction.Deposit(
            revenueAccount = revenueAccount,
            destinationAccount = usdAccount,
            amount = "2000.00",
            category = category,
            dateTime = dt,
            tag = "salary",
        )
        val text = tx.previewText()
        assertTrue(text.contains("Type: Deposit"))
        assertTrue(text.contains("From: Employer Paycheck"))
        assertTrue(text.contains("To: Checking (USD)"))
        assertTrue(text.contains("Amount: 2000.00 USD"))
        assertTrue(text.contains("Category: Food & Dining"))
        assertTrue(text.contains("Tag: salary"))
        assertTrue(text.contains("Description: (none)"))
    }

    @Test
    fun `previewText deposit without category shows dash`() {
        val tx = Transaction.Deposit(
            revenueAccount = revenueAccount,
            destinationAccount = usdAccount,
            amount = "100.00",
            category = null,
            dateTime = dt,
            tag = null,
        )
        val text = tx.previewText()
        assertTrue(text.contains("Category: -"))
        assertTrue(text.contains("Tag: (none)"))
        assertTrue(text.contains("Description: (none)"))
    }

    // ── Transfer successText ──────────────────────────────────────────────────

    @Test
    fun `successText same-currency transfer shows amount and accounts`() {
        val tx = Transaction.Transfer(
            sourceAccount = usdAccount,
            destinationAccount = savingsAccount,
            amount = "100.00",
            sourceAmount = null,
            destAmount = null,
            dateTime = dt,
            tag = null,
        )
        val text = tx.successText()
        assertTrue(text.contains("Transfer submitted successfully!"))
        assertTrue(text.contains("Checking -> Savings"))
        assertTrue(text.contains("Amount: 100.00"))
    }

    @Test
    fun `successText cross-currency transfer shows both amounts`() {
        val tx = Transaction.Transfer(
            sourceAccount = usdAccount,
            destinationAccount = eurAccount,
            amount = null,
            sourceAmount = "100.00",
            destAmount = "92.00",
            dateTime = dt,
            tag = null,
        )
        val text = tx.successText()
        assertTrue(text.contains("Transfer submitted successfully!"))
        assertTrue(text.contains("100.00 USD / 92.00 EUR"))
    }

    // ── Withdrawal successText ────────────────────────────────────────────────

    @Test
    fun `successText withdrawal shows source, expense, amount and category`() {
        val tx = Transaction.Withdrawal(
            sourceAccount = usdAccount,
            expenseAccount = expenseAccount,
            amount = "50.00",
            category = category,
            dateTime = dt,
            tag = "food",
        )
        val text = tx.successText()
        assertTrue(text.contains("Withdrawal submitted successfully!"))
        assertTrue(text.contains("From: Checking"))
        assertTrue(text.contains("To: Grocery Store"))
        assertTrue(text.contains("Amount: 50.00 USD"))
        assertTrue(text.contains("Category: Food & Dining"))
        assertTrue(text.contains("Tag: food"))
    }

    @Test
    fun `successText withdrawal without category shows dash`() {
        val tx = Transaction.Withdrawal(
            sourceAccount = usdAccount,
            expenseAccount = expenseAccount,
            amount = "50.00",
            category = null,
            dateTime = dt,
            tag = null,
        )
        val text = tx.successText()
        assertTrue(text.contains("Category: -"))
        assertFalse(text.contains("Tag: food"))
        assertTrue(text.contains("Tag: (none)"))
    }

    // ── Deposit successText ───────────────────────────────────────────────────

    @Test
    fun `successText deposit shows revenue, destination, amount and category`() {
        val tx = Transaction.Deposit(
            revenueAccount = revenueAccount,
            destinationAccount = usdAccount,
            amount = "2000.00",
            category = null,
            dateTime = dt,
            tag = "salary",
        )
        val text = tx.successText()
        assertTrue(text.contains("Deposit submitted successfully!"))
        assertTrue(text.contains("From: Employer Paycheck"))
        assertTrue(text.contains("To: Checking"))
        assertTrue(text.contains("Amount: 2000.00 USD"))
        assertTrue(text.contains("Tag: salary"))
    }

    // ── DateTime formatting ───────────────────────────────────────────────────

    @Test
    fun `previewText formats datetime as DD-MM-YYYY HH-mm`() {
        val specificDt = LocalDateTime.of(2025, 12, 1, 9, 5)
        val tx = Transaction.Withdrawal(
            sourceAccount = usdAccount,
            expenseAccount = expenseAccount,
            amount = "10.00",
            category = null,
            dateTime = specificDt,
            tag = null,
        )
        val text = tx.previewText()
        assertTrue(text.contains("Date: 01.12.2025 09:05"))
    }

    // ── Description field ─────────────────────────────────────────────────────

    @Test
    fun `previewText shows description when set`() {
        val tx = Transaction.Withdrawal(
            sourceAccount = usdAccount,
            expenseAccount = expenseAccount,
            amount = "20.00",
            category = null,
            dateTime = dt,
            tag = null,
            description = "Lunch with colleagues",
        )
        val text = tx.previewText()
        assertTrue(text.contains("Description: Lunch with colleagues"))
    }

    @Test
    fun `successText shows description when set`() {
        val tx = Transaction.Transfer(
            sourceAccount = usdAccount,
            destinationAccount = savingsAccount,
            amount = "500.00",
            sourceAmount = null,
            destAmount = null,
            dateTime = dt,
            tag = null,
            description = "Monthly savings",
        )
        val text = tx.successText()
        assertTrue(text.contains("Description: Monthly savings"))
    }

    @Test
    fun `successText shows none when description is null`() {
        val tx = Transaction.Deposit(
            revenueAccount = revenueAccount,
            destinationAccount = usdAccount,
            amount = "100.00",
            category = null,
            dateTime = dt,
            tag = null,
        )
        val text = tx.successText()
        assertTrue(text.contains("Description: (none)"))
    }
}
