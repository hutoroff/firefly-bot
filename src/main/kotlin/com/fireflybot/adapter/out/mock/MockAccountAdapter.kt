package com.fireflybot.adapter.out.mock

import com.fireflybot.application.port.out.AccountRepository
import com.fireflybot.domain.model.Account

class MockAccountAdapter : AccountRepository {

    override fun getAssetAccounts(): List<Account> = MockData.accounts

    override fun searchExpenseAccounts(query: String): List<Account> =
        MockData.expenseAccounts.filter { it.name.contains(query, ignoreCase = true) }

    override fun searchRevenueAccounts(query: String): List<Account> =
        MockData.revenueAccounts.filter { it.name.contains(query, ignoreCase = true) }

    override fun createExpenseAccount(name: String, currencyCode: String): Account =
        Account(id = "new_${System.currentTimeMillis()}", name = name, currencyCode = currencyCode)

    override fun createRevenueAccount(name: String, currencyCode: String): Account =
        Account(id = "new_${System.currentTimeMillis()}", name = name, currencyCode = currencyCode)
}
