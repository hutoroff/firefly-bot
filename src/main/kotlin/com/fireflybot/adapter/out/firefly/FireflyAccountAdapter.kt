package com.fireflybot.adapter.out.firefly

import com.fireflybot.adapter.out.firefly.dto.AccountListResponse
import com.fireflybot.adapter.out.firefly.dto.AccountRead
import com.fireflybot.adapter.out.firefly.dto.AccountSingleResponse
import com.fireflybot.adapter.out.firefly.dto.StoreAccountRequest
import com.fireflybot.application.port.out.AccountRepository
import com.fireflybot.config.AppConfig
import com.fireflybot.domain.model.Account
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class FireflyAccountAdapter(
    private val config: AppConfig,
    private val httpClient: HttpClient,
) : AccountRepository {

    private val log = KotlinLogging.logger {}
    private val baseUrl = config.fireflyHost.trimEnd('/')

    override fun getAssetAccounts(): List<Account> {
        log.info { "Fetching asset accounts" }
        return fetchAllAccountPages("$baseUrl/api/v1/accounts", mapOf("type" to "asset"))
    }

    override fun searchExpenseAccounts(query: String): List<Account> {
        log.info { "Searching expense accounts" }
        return fetchAllAccountPages(
            "$baseUrl/api/v1/search/accounts",
            mapOf("query" to query, "type" to "expense", "field" to "name_or_iban"),
        )
    }

    override fun searchRevenueAccounts(query: String): List<Account> {
        log.info { "Searching revenue accounts" }
        return fetchAllAccountPages(
            "$baseUrl/api/v1/search/accounts",
            mapOf("query" to query, "type" to "revenue", "field" to "name_or_iban"),
        )
    }

    override fun createExpenseAccount(name: String, currencyCode: String): Account =
        createAccount(name, "expense", currencyCode)

    override fun createRevenueAccount(name: String, currencyCode: String): Account =
        createAccount(name, "revenue", currencyCode)

    private fun createAccount(name: String, type: String, currencyCode: String): Account {
        log.info { "Creating $type account" }
        return kotlinx.coroutines.runBlocking {
            httpClient.post("$baseUrl/api/v1/accounts") {
                setBody(StoreAccountRequest(name = name, type = type, currencyCode = currencyCode))
            }.body<AccountSingleResponse>().data.toDomain()
        }
    }

    private fun fetchAllAccountPages(url: String, params: Map<String, String>): List<Account> =
        buildList {
            var page = 1
            do {
                val response = kotlinx.coroutines.runBlocking {
                    httpClient.get(url) {
                        params.forEach { (k, v) -> parameter(k, v) }
                        parameter("page", page)
                    }.body<AccountListResponse>()
                }
                addAll(response.data.map { it.toDomain() })
                val totalPages = response.meta?.pagination?.totalPages ?: 1
                page++
            } while (page <= totalPages)
        }

    private fun AccountRead.toDomain() = Account(
        id = id,
        name = attributes.name,
        currencyCode = attributes.currencyCode
            ?: error("Account $id ('${attributes.name}') has no currency_code in Firefly III response"),
    )
}
