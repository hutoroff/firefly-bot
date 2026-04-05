package com.fireflybot.adapter.out.persistence

import com.fireflybot.application.port.out.WizardSessionRepository
import com.fireflybot.application.wizard.WizardSession
import com.fireflybot.application.wizard.WizardStep
import com.fireflybot.domain.model.Account
import com.fireflybot.domain.model.Category
import com.fireflybot.domain.model.TransactionType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.LocalDateTime
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val log = KotlinLogging.logger {}

class JsonFileWizardSessionRepository(filePath: String) : WizardSessionRepository {

    private val file = File(filePath)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = ReentrantLock()

    override fun get(chatId: Long): WizardSession? = lock.withLock {
        readMap()[chatId.toString()]?.let { dto ->
            try {
                dto.toSession()
            } catch (e: Exception) {
                log.warn { "Dropping corrupted session for chatId=$chatId: ${e.message}" }
                null
            }
        }
    }

    override fun save(session: WizardSession) = lock.withLock {
        val map = readMap().toMutableMap()
        map[session.chatId.toString()] = session.toDto()
        writeMap(map)
    }

    override fun delete(chatId: Long) = lock.withLock {
        val map = readMap().toMutableMap()
        map.remove(chatId.toString())
        writeMap(map)
    }

    // ── File I/O ──────────────────────────────────────────────────────────────

    private fun readMap(): Map<String, WizardSessionDto> {
        if (!file.exists()) return emptyMap()
        return try {
            json.decodeFromString(file.readText())
        } catch (e: Exception) {
            log.warn { "Failed to parse session file '${file.path}', starting fresh: ${e.message}" }
            emptyMap()
        }
    }

    private fun writeMap(map: Map<String, WizardSessionDto>) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(map))
        try {
            Files.move(tmp.toPath(), file.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), REPLACE_EXISTING)
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun WizardSession.toDto() = WizardSessionDto(
        chatId = chatId,
        step = step::class.simpleName!!,
        transactionType = transactionType?.name,
        sourceAccountId = sourceAccount?.id,
        sourceAccountName = sourceAccount?.name,
        sourceAccountCurrencyCode = sourceAccount?.currencyCode,
        destinationAccountId = destinationAccount?.id,
        destinationAccountName = destinationAccount?.name,
        destinationAccountCurrencyCode = destinationAccount?.currencyCode,
        amount = amount,
        sourceAmount = sourceAmount,
        destAmount = destAmount,
        dateTime = dateTime.toString(),
        tag = tag,
        description = description,
        categoryId = category?.id,
        categoryName = category?.name,
        expenseAccountQuery = expenseAccountQuery,
        revenueAccountQuery = revenueAccountQuery,
        accountPage = accountPage,
        wizardMessageId = wizardMessageId,
    )

    private fun WizardSessionDto.toSession() = WizardSession(
        chatId = chatId,
        step = step.toWizardStep(),
        transactionType = transactionType?.let { TransactionType.valueOf(it) },
        sourceAccount = buildAccount(sourceAccountId, sourceAccountName, sourceAccountCurrencyCode),
        destinationAccount = buildAccount(destinationAccountId, destinationAccountName, destinationAccountCurrencyCode),
        amount = amount,
        sourceAmount = sourceAmount,
        destAmount = destAmount,
        dateTime = LocalDateTime.parse(dateTime),
        tag = tag,
        description = description,
        category = buildCategory(categoryId, categoryName),
        expenseAccountQuery = expenseAccountQuery,
        revenueAccountQuery = revenueAccountQuery,
        accountPage = accountPage,
        wizardMessageId = wizardMessageId,
    )

    private fun buildAccount(id: String?, name: String?, currencyCode: String?): Account? {
        if (id == null) return null
        return Account(id, name!!, currencyCode!!)
    }

    private fun buildCategory(id: String?, name: String?): Category? {
        if (id == null) return null
        return Category(id, name!!)
    }

    private fun String.toWizardStep(): WizardStep = when (this) {
        "SelectType" -> WizardStep.SelectType
        "SelectSourceAccount" -> WizardStep.SelectSourceAccount
        "SelectDestinationAccount" -> WizardStep.SelectDestinationAccount
        "EnterAmount" -> WizardStep.EnterAmount
        "EnterSourceAmount" -> WizardStep.EnterSourceAmount
        "EnterDestAmount" -> WizardStep.EnterDestAmount
        "EnterExpenseAccountQuery" -> WizardStep.EnterExpenseAccountQuery
        "EnterNewExpenseAccountName" -> WizardStep.EnterNewExpenseAccountName
        "EnterRevenueAccountQuery" -> WizardStep.EnterRevenueAccountQuery
        "EnterNewRevenueAccountName" -> WizardStep.EnterNewRevenueAccountName
        "SelectCategory" -> WizardStep.SelectCategory
        "Preview" -> WizardStep.Preview
        "EnterDateTime" -> WizardStep.EnterDateTime
        "SelectTag" -> WizardStep.SelectTag
        "EnterDescription" -> WizardStep.EnterDescription
        else -> error("Unknown WizardStep: '$this'")
    }
}
