package com.fireflybot.firefly

import com.fireflybot.config.AppConfig
import com.fireflybot.firefly.model.StoreTransactionRequest
import com.fireflybot.firefly.model.TransactionSingle
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class FireflyClient(
    private val config: AppConfig,
    private val httpClient: HttpClient,
) {
    private val log = KotlinLogging.logger {}
    private val baseUrl = config.fireflyHost.trimEnd('/')

    suspend fun createTransaction(request: StoreTransactionRequest): TransactionSingle {
        val description = request.transactions.firstOrNull()?.description ?: "unknown"
        log.info { "Creating transaction: $description" }
        return try {
            val result: TransactionSingle = httpClient.post("$baseUrl/api/v1/transactions") {
                setBody(request)
            }.body()
            log.info { "Transaction created successfully, id=${result.data.id}" }
            result
        } catch (e: Exception) {
            log.error(e) { "Failed to create transaction: $description" }
            throw e
        }
    }
}
