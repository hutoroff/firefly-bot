package com.fireflybot.application.port.out

import com.fireflybot.domain.model.Account

interface AccountRepository {
    fun getAssetAccounts(): List<Account>
    fun searchExpenseAccounts(query: String): List<Account>
    fun searchRevenueAccounts(query: String): List<Account>
    fun createExpenseAccount(name: String, currencyCode: String): Account
    fun createRevenueAccount(name: String, currencyCode: String): Account
}
