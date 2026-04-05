package com.fireflybot.adapter.out.mock

import com.fireflybot.domain.model.Account
import com.fireflybot.domain.model.Category

object MockData {
    val accounts = listOf(
        Account("1", "Main Checking", "USD"),
        Account("2", "Savings Account", "USD"),
        Account("3", "Euro Account", "EUR"),
        Account("4", "Investment Portfolio", "USD"),
        Account("5", "Cash Wallet", "USD"),
        Account("6", "Travel Card", "EUR"),
        Account("7", "Emergency Fund", "GBP"),
    )

    val expenseAccounts = listOf(
        Account("e1", "Grocery Store", "USD"),
        Account("e2", "Restaurant", "USD"),
        Account("e3", "Gas Station", "USD"),
        Account("e4", "Amazon", "USD"),
        Account("e5", "Netflix", "USD"),
        Account("e6", "Pharmacy", "USD"),
        Account("e7", "Uber", "USD"),
        Account("e8", "Coffee Shop", "EUR"),
        Account("e9", "Landlord", "USD"),
        Account("e10", "Electric Company", "USD"),
    )

    val revenueAccounts = listOf(
        Account("r1", "Employer Paycheck", "USD"),
        Account("r2", "Freelance Income", "USD"),
        Account("r3", "Bank Interest", "USD"),
        Account("r4", "Rental Income", "EUR"),
        Account("r5", "Dividends", "USD"),
        Account("r6", "Tax Refund", "USD"),
        Account("r7", "Gift", "USD"),
        Account("r8", "Pension", "GBP"),
    )

    val categories = listOf(
        Category("1", "Food & Dining"),
        Category("2", "Transport"),
        Category("3", "Shopping"),
        Category("4", "Entertainment"),
        Category("5", "Healthcare"),
        Category("6", "Utilities"),
        Category("7", "Subscriptions"),
        Category("8", "Personal Care"),
        Category("9", "Housing"),
        Category("10", "Education"),
    )

    val tags = listOf(
        "food", "transport", "utilities", "entertainment",
        "healthcare", "salary", "rent", "travel",
    )
}
