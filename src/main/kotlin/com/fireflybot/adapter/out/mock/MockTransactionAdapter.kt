package com.fireflybot.adapter.out.mock

import com.fireflybot.application.port.out.TransactionRepository
import com.fireflybot.domain.model.Transaction
import io.github.oshai.kotlinlogging.KotlinLogging

class MockTransactionAdapter : TransactionRepository {

    private val log = KotlinLogging.logger {}

    override fun createTransaction(transaction: Transaction): String {
        val mockId = "mock-${System.currentTimeMillis()}"
        log.info { "Mock: submitted ${transaction::class.simpleName} id=$mockId" }
        return mockId
    }
}
