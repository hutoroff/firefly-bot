package com.fireflybot.adapter.out.firefly

import com.fireflybot.adapter.out.firefly.dto.StoreTransactionRequest
import com.fireflybot.adapter.out.firefly.dto.TransactionSingle
import com.fireflybot.adapter.out.firefly.dto.TransactionSplit
import com.fireflybot.application.port.out.TransactionRepository
import com.fireflybot.config.AppConfig
import com.fireflybot.domain.model.Transaction
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class FireflyTransactionAdapter(
    private val config: AppConfig,
    private val httpClient: HttpClient,
) : TransactionRepository {

    private val log = KotlinLogging.logger {}
    private val baseUrl = config.fireflyHost.trimEnd('/')
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")

    override fun createTransaction(transaction: Transaction): String {
        val request = transaction.toRequest()
        val type = request.transactions.firstOrNull()?.type ?: "unknown"
        log.info { "Creating transaction type=$type" }
        return try {
            val result: TransactionSingle = kotlinx.coroutines.runBlocking {
                httpClient.post("$baseUrl/api/v1/transactions") {
                    setBody(request)
                }.body()
            }
            log.info { "Transaction created successfully, id=${result.data.id}" }
            result.data.id
        } catch (e: Exception) {
            log.error(e) { "Failed to create transaction type=$type" }
            throw e
        }
    }

    private fun Transaction.toRequest(): StoreTransactionRequest {
        val isoDate = dateTime.atOffset(ZoneOffset.UTC).format(isoFormatter)
        val tagsList = listOfNotNull("tg", tag).distinct()
        val split = when (this) {
            is Transaction.Transfer -> TransactionSplit(
                type = "transfer",
                date = isoDate,
                amount = amount ?: sourceAmount ?: "0",
                description = description ?: "Transfer: ${sourceAccount.name} → ${destinationAccount.name}",
                sourceId = sourceAccount.id,
                destinationId = destinationAccount.id,
                tags = tagsList,
            )
            is Transaction.Withdrawal -> TransactionSplit(
                type = "withdrawal",
                date = isoDate,
                amount = amount,
                description = description ?: "Withdrawal to ${expenseAccount.name}",
                sourceId = sourceAccount.id,
                destinationName = expenseAccount.name,
                category = category?.name,
                tags = tagsList,
            )
            is Transaction.Deposit -> TransactionSplit(
                type = "deposit",
                date = isoDate,
                amount = amount,
                description = description ?: "Deposit from ${revenueAccount.name}",
                sourceName = revenueAccount.name,
                destinationId = destinationAccount.id,
                category = category?.name,
                tags = tagsList,
            )
        }
        return StoreTransactionRequest(transactions = listOf(split))
    }
}
