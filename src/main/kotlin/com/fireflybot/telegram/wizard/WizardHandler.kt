package com.fireflybot.telegram.wizard

import com.fireflybot.firefly.MockData
import com.fireflybot.firefly.model.Account
import com.fireflybot.firefly.model.Category
import io.github.oshai.kotlinlogging.KotlinLogging
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class WizardHandler(private val sessionStore: WizardSessionStore) {

    private val log = KotlinLogging.logger {}
    private val dtFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    private val accounts = MockData.accounts
    private val tags = MockData.tags
    private val pageSize = 5

    // ── Entry point ───────────────────────────────────────────────────────────

    fun handleNew(chatId: Long, bot: TelegramLongPollingBot) {
        val session = sessionStore.create(chatId)
        val keyboard = inlineKeyboard(listOf(
            listOf(button("Transfer", "t:transfer")),
            listOf(button("Withdrawal", "t:withdrawal")),
            listOf(button("Deposit", "t:deposit")),
        ))
        val msg = sendMessage(bot, chatId, "Select transaction type:", keyboard) ?: return
        sessionStore.update(session.copy(wizardMessageId = msg.messageId))
    }

    // ── Callback query ────────────────────────────────────────────────────────

    fun handleCallback(query: CallbackQuery, bot: TelegramLongPollingBot) {
        val chatId = query.message.chatId
        val messageId = query.message.messageId
        val data = query.data ?: return

        answerCallback(bot, query.id)

        val session = sessionStore.get(chatId)
        if (session == null) {
            editMessage(bot, chatId, messageId, "Session expired. Use /new to start again.")
            return
        }

        when {
            data.startsWith("t:") -> handleTypeSelection(session, chatId, messageId, data.removePrefix("t:"), bot)
            // source account (all types)
            data.startsWith("sa:") -> handleSourceAccountSelected(session, chatId, messageId, data.removePrefix("sa:"), bot)
            data.startsWith("sa_p:") -> data.removePrefix("sa_p:").toIntOrNull()
                ?.let { handleSourceAccountPage(session, chatId, messageId, it, bot) }
            // destination account (transfer)
            data.startsWith("da:") -> handleDestAccountSelected(session, chatId, messageId, data.removePrefix("da:"), bot)
            data.startsWith("da_p:") -> data.removePrefix("da_p:").toIntOrNull()
                ?.let { handleDestAccountPage(session, chatId, messageId, it, bot) }
            // revenue account (deposit) — check ra_p: before ra: to avoid prefix collision
            data.startsWith("ra_p:") -> data.removePrefix("ra_p:").toIntOrNull()
                ?.let { handleRevenueAccountPage(session, chatId, messageId, it, bot) }
            data.startsWith("ra:") -> handleRevenueAccountSelected(session, chatId, messageId, data.removePrefix("ra:"), bot)
            data == "ra_new" -> handleNewRevenueAccountButton(session, chatId, messageId, bot)
            // expense account (withdrawal) — check ea_p: before ea: to avoid prefix collision
            data.startsWith("ea_p:") -> data.removePrefix("ea_p:").toIntOrNull()
                ?.let { handleExpenseAccountPage(session, chatId, messageId, it, bot) }
            data.startsWith("ea:") -> handleExpenseAccountSelected(session, chatId, messageId, data.removePrefix("ea:"), bot)
            data == "ea_new" -> handleNewExpenseAccountButton(session, chatId, messageId, bot)
            // category (withdrawal) — check cat_p: before cat: to avoid prefix collision
            data.startsWith("cat_p:") -> data.removePrefix("cat_p:").toIntOrNull()
                ?.let { handleCategoryPage(session, chatId, messageId, it, bot) }
            data.startsWith("cat:") -> handleCategorySelected(session, chatId, messageId, data.removePrefix("cat:"), bot)
            // preview actions (all types)
            data.startsWith("pv:") -> handlePreviewAction(session, chatId, messageId, data.removePrefix("pv:"), bot)
            data.startsWith("tg:") -> handleTagSelected(session, chatId, messageId, data.removePrefix("tg:"), bot)
        }
    }

    // ── Text message ──────────────────────────────────────────────────────────

    fun handleText(message: Message, bot: TelegramLongPollingBot) {
        val chatId = message.chatId
        val text = message.text ?: return
        val session = sessionStore.get(chatId) ?: return
        val wizardMsgId = session.wizardMessageId ?: return

        when (session.step) {
            WizardStep.EnterAmount -> handleAmountInput(session, chatId, wizardMsgId, text, bot)
            WizardStep.EnterSourceAmount -> handleSourceAmountInput(session, chatId, wizardMsgId, text, bot)
            WizardStep.EnterDestAmount -> handleDestAmountInput(session, chatId, wizardMsgId, text, bot)
            WizardStep.EnterDateTime -> handleDateTimeInput(session, chatId, wizardMsgId, text, bot)
            WizardStep.EnterExpenseAccountQuery -> handleExpenseAccountQuery(session, chatId, wizardMsgId, text, bot)
            WizardStep.EnterNewExpenseAccountName -> handleNewExpenseAccountName(session, chatId, wizardMsgId, text, bot)
            WizardStep.EnterRevenueAccountQuery -> handleRevenueAccountQuery(session, chatId, wizardMsgId, text, bot)
            WizardStep.EnterNewRevenueAccountName -> handleNewRevenueAccountName(session, chatId, wizardMsgId, text, bot)
            else -> {}
        }
    }

    // ── Type selection ────────────────────────────────────────────────────────

    private fun handleTypeSelection(
        session: WizardSession, chatId: Long, messageId: Int, type: String, bot: TelegramLongPollingBot,
    ) {
        if (type !in setOf("transfer", "withdrawal", "deposit")) {
            val label = type.replaceFirstChar { it.uppercase() }
            editMessage(bot, chatId, messageId, "$label is not yet supported. Use /new to start again.")
            sessionStore.remove(chatId)
            return
        }
        // Deposit collects destination (asset) first; transfer and withdrawal collect source first.
        val firstStep = if (type == "deposit") WizardStep.SelectDestinationAccount else WizardStep.SelectSourceAccount
        val updated = session.copy(
            step = firstStep,
            type = type,
            wizardMessageId = messageId,
            accountPage = 0,
        )
        sessionStore.update(updated)
        if (type == "deposit") showDestAccountSelection(updated, chatId, messageId, bot)
        else showSourceAccountSelection(updated, chatId, messageId, bot)
    }

    // ── Source account (all types) ────────────────────────────────────────────

    private fun showSourceAccountSelection(session: WizardSession, chatId: Long, messageId: Int, bot: TelegramLongPollingBot) {
        val keyboard = buildAccountKeyboard(accounts, session.accountPage, "sa", "sa_p")
        editMessage(bot, chatId, messageId, "Select source account:", keyboard)
    }

    private fun handleSourceAccountSelected(
        session: WizardSession, chatId: Long, messageId: Int, accountId: String, bot: TelegramLongPollingBot,
    ) {
        val account = accounts.find { it.id == accountId } ?: return
        when (session.type) {
            "transfer" -> {
                val updated = session.copy(
                    step = WizardStep.SelectDestinationAccount,
                    sourceAccount = account,
                    accountPage = 0,
                )
                sessionStore.update(updated)
                showDestAccountSelection(updated, chatId, messageId, bot)
            }
            "withdrawal" -> {
                val updated = session.copy(
                    step = WizardStep.EnterExpenseAccountQuery,
                    sourceAccount = account,
                )
                sessionStore.update(updated)
                editMessage(bot, chatId, messageId, "Enter part of the expense account name to search:")
            }
        }
    }

    private fun handleSourceAccountPage(
        session: WizardSession, chatId: Long, messageId: Int, page: Int, bot: TelegramLongPollingBot,
    ) {
        val updated = session.copy(accountPage = page)
        sessionStore.update(updated)
        showSourceAccountSelection(updated, chatId, messageId, bot)
    }

    // ── Destination account (transfer) ────────────────────────────────────────

    private fun showDestAccountSelection(session: WizardSession, chatId: Long, messageId: Int, bot: TelegramLongPollingBot) {
        val eligible = accounts.filter { it.id != session.sourceAccount?.id }
        val keyboard = buildAccountKeyboard(eligible, session.accountPage, "da", "da_p")
        editMessage(bot, chatId, messageId, "Select destination account:", keyboard)
    }

    private fun handleDestAccountSelected(
        session: WizardSession, chatId: Long, messageId: Int, accountId: String, bot: TelegramLongPollingBot,
    ) {
        if (accountId == session.sourceAccount?.id) return
        val account = accounts.find { it.id == accountId } ?: return
        when (session.type) {
            "transfer" -> {
                val sameCurrency = session.sourceAccount?.currencyCode == account.currencyCode
                val nextStep = if (sameCurrency) WizardStep.EnterAmount else WizardStep.EnterSourceAmount
                val updated = session.copy(step = nextStep, destinationAccount = account)
                sessionStore.update(updated)
                showAmountRequest(updated, chatId, messageId, bot)
            }
            "deposit" -> {
                val updated = session.copy(
                    step = WizardStep.EnterRevenueAccountQuery,
                    destinationAccount = account,
                )
                sessionStore.update(updated)
                editMessage(bot, chatId, messageId, "Enter part of the revenue account name to search:")
            }
        }
    }

    private fun handleDestAccountPage(
        session: WizardSession, chatId: Long, messageId: Int, page: Int, bot: TelegramLongPollingBot,
    ) {
        val updated = session.copy(accountPage = page)
        sessionStore.update(updated)
        showDestAccountSelection(updated, chatId, messageId, bot)
    }

    // ── Expense account search (withdrawal) ───────────────────────────────────

    private fun handleExpenseAccountQuery(
        session: WizardSession, chatId: Long, messageId: Int, query: String, bot: TelegramLongPollingBot,
    ) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            editMessage(bot, chatId, messageId, "Please enter part of the expense account name:")
            return
        }
        val updated = session.copy(expenseAccountQuery = trimmed, accountPage = 0)
        sessionStore.update(updated)
        showExpenseAccountResults(updated, chatId, messageId, bot)
    }

    private fun handleExpenseAccountPage(
        session: WizardSession, chatId: Long, messageId: Int, page: Int, bot: TelegramLongPollingBot,
    ) {
        val updated = session.copy(accountPage = page)
        sessionStore.update(updated)
        showExpenseAccountResults(updated, chatId, messageId, bot)
    }

    private fun showExpenseAccountResults(
        session: WizardSession, chatId: Long, messageId: Int, bot: TelegramLongPollingBot,
    ) {
        val query = session.expenseAccountQuery ?: return
        val matches = MockData.expenseAccounts.filter { it.name.contains(query, ignoreCase = true) }
        val page = session.accountPage
        val start = page * pageSize
        val pageMatches = matches.drop(start).take(pageSize)
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        for (account in pageMatches) {
            rows += listOf(button(account.name, "ea:${account.id}"))
        }
        val navRow = mutableListOf<InlineKeyboardButton>()
        if (page > 0) navRow += button("← Prev", "ea_p:${page - 1}")
        if (start + pageSize < matches.size) navRow += button("Next →", "ea_p:${page + 1}")
        if (navRow.isNotEmpty()) rows += navRow
        rows += listOf(button("+ Create new expense account", "ea_new"))
        val total = matches.size
        val prompt = if (total == 0) {
            "No accounts found for \"$query\". You can create a new one:"
        } else {
            "Found $total account(s) for \"$query\". Select or create new:"
        }
        editMessage(bot, chatId, messageId, prompt, inlineKeyboard(rows))
    }

    private fun handleExpenseAccountSelected(
        session: WizardSession, chatId: Long, messageId: Int, accountId: String, bot: TelegramLongPollingBot,
    ) {
        val account = MockData.expenseAccounts.find { it.id == accountId } ?: return
        val srcCurrency = session.sourceAccount?.currencyCode
        if (account.currencyCode != srcCurrency) {
            editMessage(
                bot, chatId, messageId,
                "Currency mismatch: source account is $srcCurrency but \"${account.name}\" uses ${account.currencyCode}.\n" +
                    "Please search for a different expense account:",
            )
            return
        }
        val updated = session.copy(
            step = WizardStep.EnterAmount,
            destinationAccount = account,
        )
        sessionStore.update(updated)
        showAmountRequest(updated, chatId, messageId, bot)
    }

    private fun handleNewExpenseAccountButton(
        session: WizardSession, chatId: Long, messageId: Int, bot: TelegramLongPollingBot,
    ) {
        sessionStore.update(session.copy(step = WizardStep.EnterNewExpenseAccountName))
        editMessage(bot, chatId, messageId, "Enter the name for the new expense account:")
    }

    private fun handleNewExpenseAccountName(
        session: WizardSession, chatId: Long, messageId: Int, text: String, bot: TelegramLongPollingBot,
    ) {
        val name = text.trim()
        if (name.isEmpty()) {
            editMessage(bot, chatId, messageId, "Account name cannot be empty. Enter the name for the new expense account:")
            return
        }
        // Mock creation — Firefly API call will be wired up in a future iteration
        val currency = session.sourceAccount?.currencyCode ?: "USD"
        val newAccount = Account(id = "new_${System.currentTimeMillis()}", name = name, currencyCode = currency)
        log.info { "Creating expense account for chat=$chatId (mocked)" }
        val updated = session.copy(
            step = WizardStep.EnterAmount,
            destinationAccount = newAccount,
        )
        sessionStore.update(updated)
        showAmountRequest(updated, chatId, messageId, bot)
    }

    // ── Revenue account search (deposit) ─────────────────────────────────────

    private fun handleRevenueAccountQuery(
        session: WizardSession, chatId: Long, messageId: Int, query: String, bot: TelegramLongPollingBot,
    ) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            editMessage(bot, chatId, messageId, "Please enter part of the revenue account name:")
            return
        }
        val updated = session.copy(revenueAccountQuery = trimmed, accountPage = 0)
        sessionStore.update(updated)
        showRevenueAccountResults(updated, chatId, messageId, bot)
    }

    private fun handleRevenueAccountPage(
        session: WizardSession, chatId: Long, messageId: Int, page: Int, bot: TelegramLongPollingBot,
    ) {
        val updated = session.copy(accountPage = page)
        sessionStore.update(updated)
        showRevenueAccountResults(updated, chatId, messageId, bot)
    }

    private fun showRevenueAccountResults(
        session: WizardSession, chatId: Long, messageId: Int, bot: TelegramLongPollingBot,
    ) {
        val query = session.revenueAccountQuery ?: return
        val matches = MockData.revenueAccounts.filter { it.name.contains(query, ignoreCase = true) }
        val page = session.accountPage
        val start = page * pageSize
        val pageMatches = matches.drop(start).take(pageSize)
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        for (account in pageMatches) {
            rows += listOf(button(account.name, "ra:${account.id}"))
        }
        val navRow = mutableListOf<InlineKeyboardButton>()
        if (page > 0) navRow += button("← Prev", "ra_p:${page - 1}")
        if (start + pageSize < matches.size) navRow += button("Next →", "ra_p:${page + 1}")
        if (navRow.isNotEmpty()) rows += navRow
        rows += listOf(button("+ Create new revenue account", "ra_new"))
        val total = matches.size
        val prompt = if (total == 0) {
            "No accounts found for \"$query\". You can create a new one:"
        } else {
            "Found $total account(s) for \"$query\". Select or create new:"
        }
        editMessage(bot, chatId, messageId, prompt, inlineKeyboard(rows))
    }

    private fun handleRevenueAccountSelected(
        session: WizardSession, chatId: Long, messageId: Int, accountId: String, bot: TelegramLongPollingBot,
    ) {
        val account = MockData.revenueAccounts.find { it.id == accountId } ?: return
        val dstCurrency = session.destinationAccount?.currencyCode
        if (account.currencyCode != dstCurrency) {
            editMessage(
                bot, chatId, messageId,
                "Currency mismatch: destination account is $dstCurrency but \"${account.name}\" uses ${account.currencyCode}.\n" +
                    "Please search for a different revenue account:",
            )
            return
        }
        val updated = session.copy(step = WizardStep.EnterAmount, sourceAccount = account)
        sessionStore.update(updated)
        showAmountRequest(updated, chatId, messageId, bot)
    }

    private fun handleNewRevenueAccountButton(
        session: WizardSession, chatId: Long, messageId: Int, bot: TelegramLongPollingBot,
    ) {
        sessionStore.update(session.copy(step = WizardStep.EnterNewRevenueAccountName))
        editMessage(bot, chatId, messageId, "Enter the name for the new revenue account:")
    }

    private fun handleNewRevenueAccountName(
        session: WizardSession, chatId: Long, messageId: Int, text: String, bot: TelegramLongPollingBot,
    ) {
        val name = text.trim()
        if (name.isEmpty()) {
            editMessage(bot, chatId, messageId, "Account name cannot be empty. Enter the name for the new revenue account:")
            return
        }
        // Mock creation — Firefly API call will be wired up in a future iteration
        val currency = session.destinationAccount?.currencyCode ?: "USD"
        val newAccount = Account(id = "new_${System.currentTimeMillis()}", name = name, currencyCode = currency)
        log.info { "Creating revenue account for chat=$chatId (mocked)" }
        val updated = session.copy(step = WizardStep.EnterAmount, sourceAccount = newAccount)
        sessionStore.update(updated)
        showAmountRequest(updated, chatId, messageId, bot)
    }

    // ── Amount input ──────────────────────────────────────────────────────────

    private fun showAmountRequest(session: WizardSession, chatId: Long, messageId: Int, bot: TelegramLongPollingBot) {
        // For deposit the asset (destination) account holds the target currency; for others use source currency.
        val currency = if (session.type == "deposit") {
            session.destinationAccount?.currencyCode
        } else {
            session.sourceAccount?.currencyCode
        }
        val prompt = when (session.step) {
            WizardStep.EnterAmount -> "Enter amount in $currency:"
            WizardStep.EnterSourceAmount -> "Enter withdrawal amount in $currency:"
            else -> "Enter amount:"
        }
        editMessage(bot, chatId, messageId, prompt)
    }

    private fun handleAmountInput(
        session: WizardSession, chatId: Long, messageId: Int, text: String, bot: TelegramLongPollingBot,
    ) {
        val amount = parseAmount(text)
        if (amount == null) {
            editMessage(bot, chatId, messageId, "Invalid amount. Enter a positive number (e.g. 100.50):")
            return
        }
        val withAmount = session.copy(amount = amount)
        if (session.type == "withdrawal" || session.type == "deposit") {
            val updated = withAmount.copy(step = WizardStep.SelectCategory, accountPage = 0)
            sessionStore.update(updated)
            showCategorySelection(updated, chatId, messageId, bot)
        } else {
            val updated = withAmount.copy(step = WizardStep.Preview)
            sessionStore.update(updated)
            showPreview(updated, chatId, messageId, bot)
        }
    }

    private fun handleSourceAmountInput(
        session: WizardSession, chatId: Long, messageId: Int, text: String, bot: TelegramLongPollingBot,
    ) {
        val amount = parseAmount(text)
        if (amount == null) {
            editMessage(bot, chatId, messageId, "Invalid amount. Enter a positive number (e.g. 100.50):")
            return
        }
        val updated = session.copy(step = WizardStep.EnterDestAmount, sourceAmount = amount)
        sessionStore.update(updated)
        editMessage(bot, chatId, messageId, "Enter deposit amount in ${session.destinationAccount?.currencyCode}:")
    }

    private fun handleDestAmountInput(
        session: WizardSession, chatId: Long, messageId: Int, text: String, bot: TelegramLongPollingBot,
    ) {
        val amount = parseAmount(text)
        if (amount == null) {
            editMessage(bot, chatId, messageId, "Invalid amount. Enter a positive number (e.g. 100.50):")
            return
        }
        val updated = session.copy(step = WizardStep.Preview, destAmount = amount)
        sessionStore.update(updated)
        showPreview(updated, chatId, messageId, bot)
    }

    // ── Category selection (withdrawal) ───────────────────────────────────────

    private fun showCategorySelection(session: WizardSession, chatId: Long, messageId: Int, bot: TelegramLongPollingBot) {
        val keyboard = buildCategoryKeyboard(session.accountPage)
        editMessage(bot, chatId, messageId, "Select a category:", keyboard)
    }

    private fun handleCategorySelected(
        session: WizardSession, chatId: Long, messageId: Int, categoryId: String, bot: TelegramLongPollingBot,
    ) {
        val category = MockData.categories.find { it.id == categoryId } ?: return
        val updated = session.copy(step = WizardStep.Preview, category = category)
        sessionStore.update(updated)
        showPreview(updated, chatId, messageId, bot)
    }

    private fun handleCategoryPage(
        session: WizardSession, chatId: Long, messageId: Int, page: Int, bot: TelegramLongPollingBot,
    ) {
        val updated = session.copy(accountPage = page)
        sessionStore.update(updated)
        showCategorySelection(updated, chatId, messageId, bot)
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    private fun showPreview(session: WizardSession, chatId: Long, messageId: Int, bot: TelegramLongPollingBot) {
        val tagLabel = if (session.tag != null) "Change tag" else "Add tag"
        val keyboard = inlineKeyboard(listOf(
            listOf(button("Change date/time", "pv:dt")),
            listOf(button(tagLabel, "pv:tag")),
            listOf(button("Submit", "pv:sub")),
        ))
        editMessage(bot, chatId, messageId, buildPreviewText(session), keyboard)
    }

    private fun buildPreviewText(session: WizardSession): String = buildString {
        appendLine("Transaction Preview")
        when (session.type) {
            "transfer" -> {
                val src = session.sourceAccount
                val dst = session.destinationAccount
                val amountLine = when {
                    session.amount != null -> "${session.amount} ${src?.currencyCode}"
                    session.sourceAmount != null && session.destAmount != null ->
                        "${session.sourceAmount} ${src?.currencyCode} -> ${session.destAmount} ${dst?.currencyCode}"
                    else -> "-"
                }
                appendLine("Type: Transfer")
                appendLine("From: ${src?.name} (${src?.currencyCode})")
                appendLine("To: ${dst?.name} (${dst?.currencyCode})")
                appendLine("Amount: $amountLine")
            }
            "withdrawal" -> {
                appendLine("Type: Withdrawal")
                appendLine("From: ${session.sourceAccount?.name} (${session.sourceAccount?.currencyCode})")
                appendLine("To: ${session.destinationAccount?.name}")
                appendLine("Amount: ${session.amount} ${session.sourceAccount?.currencyCode}")
                appendLine("Category: ${session.category?.name ?: "-"}")
            }
            "deposit" -> {
                appendLine("Type: Deposit")
                appendLine("From: ${session.sourceAccount?.name}")
                appendLine("To: ${session.destinationAccount?.name} (${session.destinationAccount?.currencyCode})")
                appendLine("Amount: ${session.amount} ${session.destinationAccount?.currencyCode}")
                appendLine("Category: ${session.category?.name ?: "-"}")
            }
            else -> appendLine("Type: ${session.type}")
        }
        appendLine("Date: ${session.dateTime.format(dtFormatter)}")
        append("Tag: ${session.tag ?: "(none)"}")
    }

    // ── Preview actions ───────────────────────────────────────────────────────

    private fun handlePreviewAction(
        session: WizardSession, chatId: Long, messageId: Int, action: String, bot: TelegramLongPollingBot,
    ) {
        when (action) {
            "dt" -> {
                sessionStore.update(session.copy(step = WizardStep.EnterDateTime))
                editMessage(bot, chatId, messageId, "Enter date and time (DD.MM.YYYY HH:mm):")
            }
            "tag" -> {
                val updated = session.copy(step = WizardStep.SelectTag)
                sessionStore.update(updated)
                showTagSelection(chatId, messageId, bot)
            }
            "sub" -> handleSubmit(session, chatId, messageId, bot)
        }
    }

    // ── Date/time input ───────────────────────────────────────────────────────

    private fun handleDateTimeInput(
        session: WizardSession, chatId: Long, messageId: Int, text: String, bot: TelegramLongPollingBot,
    ) {
        val dt = try {
            LocalDateTime.parse(text.trim(), dtFormatter)
        } catch (e: DateTimeParseException) {
            editMessage(bot, chatId, messageId, "Invalid format. Enter date and time as DD.MM.YYYY HH:mm:")
            return
        }
        val updated = session.copy(step = WizardStep.Preview, dateTime = dt)
        sessionStore.update(updated)
        showPreview(updated, chatId, messageId, bot)
    }

    // ── Tag selection ─────────────────────────────────────────────────────────

    private fun showTagSelection(chatId: Long, messageId: Int, bot: TelegramLongPollingBot) {
        val rows = tags.chunked(2).map { row -> row.map { tag -> button(tag, "tg:$tag") } }
        editMessage(bot, chatId, messageId, "Select a tag:", inlineKeyboard(rows))
    }

    private fun handleTagSelected(
        session: WizardSession, chatId: Long, messageId: Int, tag: String, bot: TelegramLongPollingBot,
    ) {
        val updated = session.copy(step = WizardStep.Preview, tag = tag)
        sessionStore.update(updated)
        showPreview(updated, chatId, messageId, bot)
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    private fun handleSubmit(session: WizardSession, chatId: Long, messageId: Int, bot: TelegramLongPollingBot) {
        log.info {
            "Submitting ${session.type} for chat=$chatId: " +
                "source=${session.sourceAccount?.id} destination=${session.destinationAccount?.id}"
        }
        // Mock submission — real Firefly API call will be wired up in a future iteration
        sessionStore.remove(chatId)
        val result = when (session.type) {
            "transfer" -> buildString {
                appendLine("Transfer submitted successfully!")
                appendLine("From: ${session.sourceAccount?.name} -> ${session.destinationAccount?.name}")
                val amountLine = session.amount
                    ?: "${session.sourceAmount} ${session.sourceAccount?.currencyCode} / ${session.destAmount} ${session.destinationAccount?.currencyCode}"
                appendLine("Amount: $amountLine")
                appendLine("Date: ${session.dateTime.format(dtFormatter)}")
                append("Tag: ${session.tag ?: "(none)"}")
            }
            "withdrawal" -> buildString {
                appendLine("Withdrawal submitted successfully!")
                appendLine("From: ${session.sourceAccount?.name}")
                appendLine("To: ${session.destinationAccount?.name}")
                appendLine("Amount: ${session.amount} ${session.sourceAccount?.currencyCode}")
                appendLine("Category: ${session.category?.name ?: "-"}")
                appendLine("Date: ${session.dateTime.format(dtFormatter)}")
                append("Tag: ${session.tag ?: "(none)"}")
            }
            "deposit" -> buildString {
                appendLine("Deposit submitted successfully!")
                appendLine("From: ${session.sourceAccount?.name}")
                appendLine("To: ${session.destinationAccount?.name}")
                appendLine("Amount: ${session.amount} ${session.destinationAccount?.currencyCode}")
                appendLine("Category: ${session.category?.name ?: "-"}")
                appendLine("Date: ${session.dateTime.format(dtFormatter)}")
                append("Tag: ${session.tag ?: "(none)"}")
            }
            else -> "Transaction submitted."
        }
        editMessage(bot, chatId, messageId, result)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildAccountKeyboard(
        accounts: List<Account>,
        page: Int,
        selectPrefix: String,
        pagePrefix: String,
    ): InlineKeyboardMarkup {
        val start = page * pageSize
        val pageAccounts = accounts.drop(start).take(pageSize)
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        for (account in pageAccounts) {
            rows += listOf(button("${account.name} (${account.currencyCode})", "$selectPrefix:${account.id}"))
        }
        val navRow = mutableListOf<InlineKeyboardButton>()
        if (page > 0) navRow += button("← Prev", "$pagePrefix:${page - 1}")
        if (start + pageSize < accounts.size) navRow += button("Next →", "$pagePrefix:${page + 1}")
        if (navRow.isNotEmpty()) rows += navRow
        return inlineKeyboard(rows)
    }

    private fun buildCategoryKeyboard(page: Int): InlineKeyboardMarkup {
        val cats = MockData.categories
        val start = page * pageSize
        val pageCats = cats.drop(start).take(pageSize)
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        for (cat in pageCats) {
            rows += listOf(button(cat.name, "cat:${cat.id}"))
        }
        val navRow = mutableListOf<InlineKeyboardButton>()
        if (page > 0) navRow += button("← Prev", "cat_p:${page - 1}")
        if (start + pageSize < cats.size) navRow += button("Next →", "cat_p:${page + 1}")
        if (navRow.isNotEmpty()) rows += navRow
        return inlineKeyboard(rows)
    }

    private fun parseAmount(text: String): String? = try {
        val bd = text.trim().toBigDecimal()
        if (bd > java.math.BigDecimal.ZERO) bd.toPlainString() else null
    } catch (_: NumberFormatException) {
        null
    }

    private fun inlineKeyboard(rows: List<List<InlineKeyboardButton>>): InlineKeyboardMarkup =
        InlineKeyboardMarkup.builder().keyboard(rows).build()

    private fun button(text: String, callbackData: String): InlineKeyboardButton =
        InlineKeyboardButton.builder().text(text).callbackData(callbackData).build()

    private fun sendMessage(
        bot: TelegramLongPollingBot, chatId: Long, text: String, markup: InlineKeyboardMarkup? = null,
    ): Message? = try {
        bot.execute(
            SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(markup)
                .build()
        )
    } catch (e: TelegramApiException) {
        log.error(e) { "Failed to send message to chat=$chatId" }
        null
    }

    private fun editMessage(
        bot: TelegramLongPollingBot,
        chatId: Long,
        messageId: Int,
        text: String,
        markup: InlineKeyboardMarkup? = null,
    ) {
        val effectiveMarkup = markup ?: InlineKeyboardMarkup.builder().keyboard(emptyList()).build()
        try {
            bot.execute(
                EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .replyMarkup(effectiveMarkup)
                    .build()
            )
        } catch (e: TelegramApiException) {
            if (!e.message.orEmpty().contains("message is not modified")) {
                log.error(e) { "Failed to edit message=$messageId in chat=$chatId" }
            }
        }
    }

    private fun answerCallback(bot: TelegramLongPollingBot, callbackId: String) {
        try {
            bot.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build())
        } catch (e: TelegramApiException) {
            log.error(e) { "Failed to answer callback $callbackId" }
        }
    }
}
