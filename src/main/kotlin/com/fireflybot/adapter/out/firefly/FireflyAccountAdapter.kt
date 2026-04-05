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
import io.ktor.http.isSuccess

class FireflyAccountAdapter(
    private val config: AppConfig,
    private val httpClient: HttpClient,
    private val assetAccountsCache: FireflyCache<Unit, List<Account>> = FireflyCache(),
    private val expenseSearchCache: FireflyCache<String, List<Account>> = FireflyCache(),
    private val revenueSearchCache: FireflyCache<String, List<Account>> = FireflyCache(),
) : AccountRepository {

    private val log = KotlinLogging.logger {}
    private val baseUrl = config.fireflyHost.trimEnd('/')

    override fun getAssetAccounts(): List<Account> =
        assetAccountsCache.getOrLoad(Unit) {
            log.info { "Fetching asset accounts" }
            fetchAllAccountPages("$baseUrl/api/v1/accounts", mapOf("type" to "asset"))
        }

    override fun searchExpenseAccounts(query: String): List<Account> =
        expenseSearchCache.getOrLoad(query) {
            log.info { "Searching expense accounts" }
            fetchAllAccountPages(
                "$baseUrl/api/v1/search/accounts",
                mapOf("query" to query, "type" to "expense", "field" to "all"),
            )
        }

    override fun searchRevenueAccounts(query: String): List<Account> =
        revenueSearchCache.getOrLoad(query) {
            log.info { "Searching revenue accounts" }
            fetchAllAccountPages(
                "$baseUrl/api/v1/search/accounts",
                mapOf("query" to query, "type" to "revenue", "field" to "all"),
            )
        }

    override fun createExpenseAccount(name: String, currencyCode: String): Account {
        expenseSearchCache.invalidateAll()
        return createAccount(name, "expense", currencyCode)
    }

    override fun createRevenueAccount(name: String, currencyCode: String): Account {
        revenueSearchCache.invalidateAll()
        return createAccount(name, "revenue", currencyCode)
    }

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
                    val httpResponse = httpClient.get(url) {
                        params.forEach { (k, v) -> parameter(k, v) }
                        parameter("page", page)
                    }
                    if (!httpResponse.status.isSuccess()) {
                        error("Request to $url returned ${httpResponse.status.value}")
                    }
                    httpResponse.body<AccountListResponse>()
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
