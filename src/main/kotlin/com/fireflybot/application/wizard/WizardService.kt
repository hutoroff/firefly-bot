package com.fireflybot.application.wizard

import com.fireflybot.application.port.`in`.WizardUseCase
import com.fireflybot.application.port.out.AccountRepository
import com.fireflybot.application.port.out.CategoryRepository
import com.fireflybot.application.port.out.TagRepository
import com.fireflybot.application.port.out.TransactionRepository
import com.fireflybot.application.port.out.WizardSessionRepository
import com.fireflybot.domain.model.Transaction
import com.fireflybot.domain.model.TransactionType
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter

class WizardService(
    private val sessionRepository: WizardSessionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
    private val pageSize: Int = 5,
) : WizardUseCase {

    private val log = KotlinLogging.logger {}
    private val dtFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    // ── Entry points ──────────────────────────────────────────────────────────

    override fun startNewWizard(chatId: Long) {
        sessionRepository.save(WizardSession(chatId = chatId))
    }

    override fun setWizardMessageId(chatId: Long, messageId: Int) {
        val session = sessionRepository.get(chatId) ?: return
        sessionRepository.save(session.copy(wizardMessageId = messageId))
    }

    override fun getWizardMessageId(chatId: Long): Int? =
        sessionRepository.get(chatId)?.wizardMessageId

    // ── Callbacks ─────────────────────────────────────────────────────────────

    override fun handleCallback(chatId: Long, messageId: Int, data: String): WizardResult {
        val session = sessionRepository.get(chatId)
            ?: return WizardResult.SessionExpired(chatId)

        // Store the messageId in session so handleText can find it later.
        val s = if (session.wizardMessageId != messageId) {
            session.copy(wizardMessageId = messageId).also { sessionRepository.save(it) }
        } else {
            session
        }

        return when {
            data.startsWith("t:") -> handleTypeSelection(s, data.removePrefix("t:"))
            // source account (transfer, withdrawal)
            data.startsWith("sa:") -> handleSourceAccountSelected(s, data.removePrefix("sa:"))
            data.startsWith("sa_p:") -> data.removePrefix("sa_p:").toIntOrNull()
                ?.let { handleSourceAccountPage(s, it) } ?: WizardResult.NoOp
            // destination account (transfer, deposit)
            data.startsWith("da:") -> handleDestAccountSelected(s, data.removePrefix("da:"))
            data.startsWith("da_p:") -> data.removePrefix("da_p:").toIntOrNull()
                ?.let { handleDestAccountPage(s, it) } ?: WizardResult.NoOp
            // revenue account (deposit) — check ra_p: before ra: to avoid prefix collision
            data.startsWith("ra_p:") -> data.removePrefix("ra_p:").toIntOrNull()
                ?.let { handleRevenueAccountPage(s, it) } ?: WizardResult.NoOp
            data.startsWith("ra:") -> handleRevenueAccountSelected(s, data.removePrefix("ra:"))
            data == "ra_new" -> handleNewRevenueAccountButton(s)
            // expense account (withdrawal) — check ea_p: before ea: to avoid prefix collision
            data.startsWith("ea_p:") -> data.removePrefix("ea_p:").toIntOrNull()
                ?.let { handleExpenseAccountPage(s, it) } ?: WizardResult.NoOp
            data.startsWith("ea:") -> handleExpenseAccountSelected(s, data.removePrefix("ea:"))
            data == "ea_new" -> handleNewExpenseAccountButton(s)
            // category — check cat_p: before cat: to avoid prefix collision
            data.startsWith("cat_p:") -> data.removePrefix("cat_p:").toIntOrNull()
                ?.let { handleCategoryPage(s, it) } ?: WizardResult.NoOp
            data.startsWith("cat:") -> handleCategorySelected(s, data.removePrefix("cat:"))
            // preview actions
            data.startsWith("pv:") -> handlePreviewAction(s, data.removePrefix("pv:"))
            data.startsWith("tg:") -> handleTagSelected(s, data.removePrefix("tg:"))
            else -> WizardResult.NoOp
        }
    }

    // ── Text input ────────────────────────────────────────────────────────────

    override fun handleText(chatId: Long, text: String): WizardResult {
        val session = sessionRepository.get(chatId) ?: return WizardResult.NoOp

        return when (session.step) {
            WizardStep.EnterAmount -> handleAmountInput(session, text)
            WizardStep.EnterSourceAmount -> handleSourceAmountInput(session, text)
            WizardStep.EnterDestAmount -> handleDestAmountInput(session, text)
            WizardStep.EnterDateTime -> handleDateTimeInput(session, text)
            WizardStep.EnterExpenseAccountQuery -> handleExpenseAccountQuery(session, text)
            WizardStep.EnterNewExpenseAccountName -> handleNewExpenseAccountName(session, text)
            WizardStep.EnterRevenueAccountQuery -> handleRevenueAccountQuery(session, text)
            WizardStep.EnterNewRevenueAccountName -> handleNewRevenueAccountName(session, text)
            else -> WizardResult.NoOp
        }
    }

    // ── Type selection ────────────────────────────────────────────────────────

    private fun handleTypeSelection(session: WizardSession, typeValue: String): WizardResult {
        val type = TransactionType.entries.find { it.apiValue == typeValue }
            ?: return WizardResult.ShowTextPrompt(
                "${typeValue.replaceFirstChar { it.uppercase() }} is not yet supported. Use /new to start again."
            ).also { sessionRepository.delete(session.chatId) }

        // Deposit collects destination (asset) first; transfer and withdrawal collect source first.
        val firstStep = if (type == TransactionType.DEPOSIT) {
            WizardStep.SelectDestinationAccount
        } else {
            WizardStep.SelectSourceAccount
        }
        val updated = session.copy(step = firstStep, transactionType = type, accountPage = 0)
        sessionRepository.save(updated)

        return if (type == TransactionType.DEPOSIT) {
            buildDestAccountList(updated, "Select destination account:")
        } else {
            buildSourceAccountList(updated, "Select source account:")
        }
    }

    // ── Source account (transfer, withdrawal) ─────────────────────────────────

    private fun buildSourceAccountList(session: WizardSession, prompt: String): WizardResult {
        val accounts = accountRepository.getAssetAccounts()
        return WizardResult.ShowAccountList(
            prompt = prompt,
            accounts = accounts,
            page = session.accountPage,
            total = accounts.size,
            selectPrefix = "sa",
            pagePrefix = "sa_p",
        )
    }

    private fun handleSourceAccountSelected(session: WizardSession, accountId: String): WizardResult {
        val account = accountRepository.getAssetAccounts().find { it.id == accountId }
            ?: return WizardResult.NoOp
        return when (session.transactionType) {
            TransactionType.TRANSFER -> {
                val updated = session.copy(
                    step = WizardStep.SelectDestinationAccount,
                    sourceAccount = account,
                    accountPage = 0,
                    amount = null,
                    sourceAmount = null,
                    destAmount = null,
                )
                sessionRepository.save(updated)
                buildDestAccountList(updated, "Select destination account:")
            }
            TransactionType.WITHDRAWAL -> {
                val updated = session.copy(
                    step = WizardStep.EnterExpenseAccountQuery,
                    sourceAccount = account,
                )
                sessionRepository.save(updated)
                WizardResult.ShowTextPrompt("Enter part of the expense account name to search:")
            }
            else -> WizardResult.NoOp
        }
    }

    private fun handleSourceAccountPage(session: WizardSession, page: Int): WizardResult {
        val updated = session.copy(accountPage = page)
        sessionRepository.save(updated)
        return buildSourceAccountList(updated, "Select source account:")
    }

    // ── Destination account (transfer, deposit) ───────────────────────────────

    private fun buildDestAccountList(session: WizardSession, prompt: String): WizardResult {
        val accounts = accountRepository.getAssetAccounts()
            .filter { it.id != session.sourceAccount?.id }
        return WizardResult.ShowAccountList(
            prompt = prompt,
            accounts = accounts,
            page = session.accountPage,
            total = accounts.size,
            selectPrefix = "da",
            pagePrefix = "da_p",
        )
    }

    private fun handleDestAccountSelected(session: WizardSession, accountId: String): WizardResult {
        if (accountId == session.sourceAccount?.id) return WizardResult.NoOp
        val account = accountRepository.getAssetAccounts().find { it.id == accountId }
            ?: return WizardResult.NoOp
        return when (session.transactionType) {
            TransactionType.TRANSFER -> {
                val sameCurrency = session.sourceAccount?.currencyCode == account.currencyCode
                val nextStep = if (sameCurrency) WizardStep.EnterAmount else WizardStep.EnterSourceAmount
                val updated = session.copy(
                    step = nextStep,
                    destinationAccount = account,
                    amount = null,
                    sourceAmount = null,
                    destAmount = null,
                )
                sessionRepository.save(updated)
                buildAmountPrompt(updated)
            }
            TransactionType.DEPOSIT -> {
                val updated = session.copy(
                    step = WizardStep.EnterRevenueAccountQuery,
                    destinationAccount = account,
                )
                sessionRepository.save(updated)
                WizardResult.ShowTextPrompt("Enter part of the revenue account name to search:")
            }
            else -> WizardResult.NoOp
        }
    }

    private fun handleDestAccountPage(session: WizardSession, page: Int): WizardResult {
        val updated = session.copy(accountPage = page)
        sessionRepository.save(updated)
        return buildDestAccountList(updated, "Select destination account:")
    }

    // ── Expense account search (withdrawal) ───────────────────────────────────

    private fun handleExpenseAccountQuery(session: WizardSession, query: String): WizardResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return WizardResult.ShowTextPrompt("Please enter part of the expense account name:")
        }
        val updated = session.copy(expenseAccountQuery = trimmed, accountPage = 0)
        sessionRepository.save(updated)
        return buildExpenseAccountSearch(updated)
    }

    private fun handleExpenseAccountPage(session: WizardSession, page: Int): WizardResult {
        val updated = session.copy(accountPage = page)
        sessionRepository.save(updated)
        return buildExpenseAccountSearch(updated)
    }

    private fun buildExpenseAccountSearch(session: WizardSession): WizardResult {
        val query = session.expenseAccountQuery ?: return WizardResult.NoOp
        val matches = accountRepository.searchExpenseAccounts(query)
        return WizardResult.ShowAccountSearch(
            query = query,
            results = matches,
            page = session.accountPage,
            total = matches.size,
            accountKind = WizardResult.AccountKind.EXPENSE,
        )
    }

    private fun handleExpenseAccountSelected(session: WizardSession, accountId: String): WizardResult {
        val query = session.expenseAccountQuery ?: return WizardResult.NoOp
        val account = accountRepository.searchExpenseAccounts(query).find { it.id == accountId }
            ?: return WizardResult.NoOp
        val srcCurrency = session.sourceAccount?.currencyCode
        if (account.currencyCode != srcCurrency) {
            return WizardResult.ShowTextPrompt(
                "Currency mismatch: source account is $srcCurrency but \"${account.name}\" uses ${account.currencyCode}.\n" +
                    "Please search for a different expense account:"
            )
        }
        val updated = session.copy(step = WizardStep.EnterAmount, destinationAccount = account)
        sessionRepository.save(updated)
        return buildAmountPrompt(updated)
    }

    private fun handleNewExpenseAccountButton(session: WizardSession): WizardResult {
        sessionRepository.save(session.copy(step = WizardStep.EnterNewExpenseAccountName))
        return WizardResult.ShowTextPrompt("Enter the name for the new expense account:")
    }

    private fun handleNewExpenseAccountName(session: WizardSession, text: String): WizardResult {
        val name = text.trim()
        if (name.isEmpty()) {
            return WizardResult.ShowTextPrompt("Account name cannot be empty. Enter the name for the new expense account:")
        }
        val currency = session.sourceAccount?.currencyCode ?: "USD"
        val newAccount = accountRepository.createExpenseAccount(name, currency)
        log.info { "Created expense account for chat=${session.chatId}" }
        val updated = session.copy(step = WizardStep.EnterAmount, destinationAccount = newAccount)
        sessionRepository.save(updated)
        return buildAmountPrompt(updated)
    }

    // ── Revenue account search (deposit) ─────────────────────────────────────

    private fun handleRevenueAccountQuery(session: WizardSession, query: String): WizardResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return WizardResult.ShowTextPrompt("Please enter part of the revenue account name:")
        }
        val updated = session.copy(revenueAccountQuery = trimmed, accountPage = 0)
        sessionRepository.save(updated)
        return buildRevenueAccountSearch(updated)
    }

    private fun handleRevenueAccountPage(session: WizardSession, page: Int): WizardResult {
        val updated = session.copy(accountPage = page)
        sessionRepository.save(updated)
        return buildRevenueAccountSearch(updated)
    }

    private fun buildRevenueAccountSearch(session: WizardSession): WizardResult {
        val query = session.revenueAccountQuery ?: return WizardResult.NoOp
        val matches = accountRepository.searchRevenueAccounts(query)
        return WizardResult.ShowAccountSearch(
            query = query,
            results = matches,
            page = session.accountPage,
            total = matches.size,
            accountKind = WizardResult.AccountKind.REVENUE,
        )
    }

    private fun handleRevenueAccountSelected(session: WizardSession, accountId: String): WizardResult {
        val query = session.revenueAccountQuery ?: return WizardResult.NoOp
        val account = accountRepository.searchRevenueAccounts(query).find { it.id == accountId }
            ?: return WizardResult.NoOp
        val dstCurrency = session.destinationAccount?.currencyCode
        if (account.currencyCode != dstCurrency) {
            return WizardResult.ShowTextPrompt(
                "Currency mismatch: destination account is $dstCurrency but \"${account.name}\" uses ${account.currencyCode}.\n" +
                    "Please search for a different revenue account:"
            )
        }
        val updated = session.copy(step = WizardStep.EnterAmount, sourceAccount = account)
        sessionRepository.save(updated)
        return buildAmountPrompt(updated)
    }

    private fun handleNewRevenueAccountButton(session: WizardSession): WizardResult {
        sessionRepository.save(session.copy(step = WizardStep.EnterNewRevenueAccountName))
        return WizardResult.ShowTextPrompt("Enter the name for the new revenue account:")
    }

    private fun handleNewRevenueAccountName(session: WizardSession, text: String): WizardResult {
        val name = text.trim()
        if (name.isEmpty()) {
            return WizardResult.ShowTextPrompt("Account name cannot be empty. Enter the name for the new revenue account:")
        }
        val currency = session.destinationAccount?.currencyCode ?: "USD"
        val newAccount = accountRepository.createRevenueAccount(name, currency)
        log.info { "Created revenue account for chat=${session.chatId}" }
        val updated = session.copy(step = WizardStep.EnterAmount, sourceAccount = newAccount)
        sessionRepository.save(updated)
        return buildAmountPrompt(updated)
    }

    // ── Amount input ──────────────────────────────────────────────────────────

    private fun buildAmountPrompt(session: WizardSession): WizardResult {
        val currency = if (session.transactionType == TransactionType.DEPOSIT) {
            session.destinationAccount?.currencyCode
        } else {
            session.sourceAccount?.currencyCode
        }
        val prompt = when (session.step) {
            WizardStep.EnterAmount -> "Enter amount in $currency:"
            WizardStep.EnterSourceAmount -> "Enter withdrawal amount in $currency:"
            else -> "Enter amount:"
        }
        return WizardResult.ShowTextPrompt(prompt)
    }

    private fun handleAmountInput(session: WizardSession, text: String): WizardResult {
        val amount = parseAmount(text)
            ?: return WizardResult.ShowTextPrompt("Invalid amount. Enter a positive number (e.g. 100.50):")
        val withAmount = session.copy(amount = amount)
        return if (session.transactionType == TransactionType.WITHDRAWAL ||
            session.transactionType == TransactionType.DEPOSIT
        ) {
            if (session.category != null) {
                // Returning from preview — category already chosen, skip re-selection.
                val updated = withAmount.copy(step = WizardStep.Preview)
                sessionRepository.save(updated)
                WizardResult.ShowPreview(updated)
            } else {
                val updated = withAmount.copy(step = WizardStep.SelectCategory, accountPage = 0)
                sessionRepository.save(updated)
                buildCategoryList(updated)
            }
        } else {
            val updated = withAmount.copy(step = WizardStep.Preview)
            sessionRepository.save(updated)
            WizardResult.ShowPreview(updated)
        }
    }

    private fun handleSourceAmountInput(session: WizardSession, text: String): WizardResult {
        val amount = parseAmount(text)
            ?: return WizardResult.ShowTextPrompt("Invalid amount. Enter a positive number (e.g. 100.50):")
        val withSourceAmount = session.copy(sourceAmount = amount)
        return if (session.destAmount != null) {
            // Returning from preview (pv:samt) — dest amount already set, skip re-entry.
            val updated = withSourceAmount.copy(step = WizardStep.Preview)
            sessionRepository.save(updated)
            WizardResult.ShowPreview(updated)
        } else {
            val updated = withSourceAmount.copy(step = WizardStep.EnterDestAmount)
            sessionRepository.save(updated)
            WizardResult.ShowTextPrompt("Enter deposit amount in ${session.destinationAccount?.currencyCode}:")
        }
    }

    private fun handleDestAmountInput(session: WizardSession, text: String): WizardResult {
        val amount = parseAmount(text)
            ?: return WizardResult.ShowTextPrompt("Invalid amount. Enter a positive number (e.g. 100.50):")
        val updated = session.copy(step = WizardStep.Preview, destAmount = amount)
        sessionRepository.save(updated)
        return WizardResult.ShowPreview(updated)
    }

    // ── Category selection (withdrawal, deposit) ──────────────────────────────

    private fun buildCategoryList(session: WizardSession): WizardResult {
        val cats = categoryRepository.getCategories()
        return WizardResult.ShowCategoryList(
            categories = cats,
            page = session.accountPage,
            total = cats.size,
        )
    }

    private fun handleCategorySelected(session: WizardSession, categoryId: String): WizardResult {
        val category = categoryRepository.getCategories().find { it.id == categoryId }
            ?: return WizardResult.NoOp
        val updated = session.copy(step = WizardStep.Preview, category = category)
        sessionRepository.save(updated)
        return WizardResult.ShowPreview(updated)
    }

    private fun handleCategoryPage(session: WizardSession, page: Int): WizardResult {
        val updated = session.copy(accountPage = page)
        sessionRepository.save(updated)
        return buildCategoryList(updated)
    }

    // ── Preview actions ───────────────────────────────────────────────────────

    private fun handlePreviewAction(session: WizardSession, action: String): WizardResult = when (action) {
        "dt" -> {
            sessionRepository.save(session.copy(step = WizardStep.EnterDateTime))
            WizardResult.ShowTextPrompt("Enter date and time (DD.MM.YYYY HH:mm):")
        }
        "tag" -> {
            sessionRepository.save(session.copy(step = WizardStep.SelectTag))
            WizardResult.ShowTagList(tagRepository.getTags())
        }
        "sub" -> if (session.step is WizardStep.Preview) handleSubmit(session) else WizardResult.NoOp
        "sa" -> {
            val updated = session.copy(step = WizardStep.SelectSourceAccount, accountPage = 0)
            sessionRepository.save(updated)
            buildSourceAccountList(updated, "Select source account:")
        }
        "da" -> {
            val updated = session.copy(step = WizardStep.SelectDestinationAccount, accountPage = 0)
            sessionRepository.save(updated)
            buildDestAccountList(updated, "Select destination account:")
        }
        "ea" -> {
            sessionRepository.save(session.copy(step = WizardStep.EnterExpenseAccountQuery))
            WizardResult.ShowTextPrompt("Enter part of the expense account name to search:")
        }
        "ra" -> {
            sessionRepository.save(session.copy(step = WizardStep.EnterRevenueAccountQuery))
            WizardResult.ShowTextPrompt("Enter part of the revenue account name to search:")
        }
        "amt" -> {
            val updated = session.copy(step = WizardStep.EnterAmount)
            sessionRepository.save(updated)
            buildAmountPrompt(updated)
        }
        "samt" -> {
            val updated = session.copy(step = WizardStep.EnterSourceAmount)
            sessionRepository.save(updated)
            buildAmountPrompt(updated)
        }
        "damt" -> {
            val updated = session.copy(step = WizardStep.EnterDestAmount)
            sessionRepository.save(updated)
            WizardResult.ShowTextPrompt("Enter deposit amount in ${session.destinationAccount?.currencyCode}:")
        }
        "cat" -> {
            val updated = session.copy(step = WizardStep.SelectCategory, accountPage = 0)
            sessionRepository.save(updated)
            buildCategoryList(updated)
        }
        else -> WizardResult.NoOp
    }

    // ── Date/time input ───────────────────────────────────────────────────────

    private fun handleDateTimeInput(session: WizardSession, text: String): WizardResult {
        val dt = try {
            LocalDateTime.parse(text.trim(), dtFormatter)
        } catch (e: DateTimeParseException) {
            return WizardResult.ShowTextPrompt("Invalid format. Enter date and time as DD.MM.YYYY HH:mm:")
        }
        val updated = session.copy(step = WizardStep.Preview, dateTime = dt)
        sessionRepository.save(updated)
        return WizardResult.ShowPreview(updated)
    }

    // ── Tag selection ─────────────────────────────────────────────────────────

    private fun handleTagSelected(session: WizardSession, tagId: String): WizardResult {
        val tagName = tagRepository.getTags().find { it.id == tagId }?.name ?: return WizardResult.NoOp
        val updated = session.copy(step = WizardStep.Preview, tag = tagName)
        sessionRepository.save(updated)
        return WizardResult.ShowPreview(updated)
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    private fun handleSubmit(session: WizardSession): WizardResult {
        val transaction = buildTransaction(session) ?: return WizardResult.NoOp
        log.info {
            "Submitting ${session.transactionType} for chat=${session.chatId}: " +
                "source=${session.sourceAccount?.id} destination=${session.destinationAccount?.id}"
        }
        transactionRepository.createTransaction(transaction)
        sessionRepository.delete(session.chatId)
        return WizardResult.WizardComplete(transaction.successText())
    }

    private fun buildTransaction(session: WizardSession): Transaction? = when (session.transactionType) {
        TransactionType.TRANSFER -> {
            val src = session.sourceAccount ?: return null
            val dst = session.destinationAccount ?: return null
            Transaction.Transfer(
                sourceAccount = src,
                destinationAccount = dst,
                amount = session.amount,
                sourceAmount = session.sourceAmount,
                destAmount = session.destAmount,
                dateTime = session.dateTime,
                tag = session.tag,
            )
        }
        TransactionType.WITHDRAWAL -> {
            val src = session.sourceAccount ?: return null
            val exp = session.destinationAccount ?: return null
            val amount = session.amount ?: return null
            Transaction.Withdrawal(
                sourceAccount = src,
                expenseAccount = exp,
                amount = amount,
                category = session.category,
                dateTime = session.dateTime,
                tag = session.tag,
            )
        }
        TransactionType.DEPOSIT -> {
            val rev = session.sourceAccount ?: return null
            val dst = session.destinationAccount ?: return null
            val amount = session.amount ?: return null
            Transaction.Deposit(
                revenueAccount = rev,
                destinationAccount = dst,
                amount = amount,
                category = session.category,
                dateTime = session.dateTime,
                tag = session.tag,
            )
        }
        null -> null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseAmount(text: String): String? = try {
        val bd = text.trim().toBigDecimal()
        if (bd > java.math.BigDecimal.ZERO) bd.toPlainString() else null
    } catch (_: NumberFormatException) {
        null
    }
}
