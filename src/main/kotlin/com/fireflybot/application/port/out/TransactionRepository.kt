package com.fireflybot.application.port.out

import com.fireflybot.domain.model.Transaction

interface TransactionRepository {
    /** Persists the transaction and returns the remote ID assigned by Firefly III. */
    fun createTransaction(transaction: Transaction): String
}
