package com.fireflybot.adapter.`in`.telegram

import com.fireflybot.application.wizard.WizardResult
import com.fireflybot.application.wizard.WizardSession
import com.fireflybot.domain.model.Account
import com.fireflybot.domain.model.Category
import com.fireflybot.domain.model.TransactionType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.time.format.DateTimeFormatter

class TelegramWizardPresenter(private val pageSize: Int = 5) {

    private val log = KotlinLogging.logger {}
    private val dtFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun render(bot: TelegramLongPollingBot, chatId: Long, messageId: Int?, result: WizardResult) {
        when (result) {
            is WizardResult.ShowTypeSelection -> renderTypeSelection(bot, chatId, messageId)
            is WizardResult.ShowAccountList -> renderAccountList(bot, chatId, messageId, result)
            is WizardResult.ShowAccountSearch -> renderAccountSearch(bot, chatId, messageId, result)
            is WizardResult.ShowTextPrompt -> edit(bot, chatId, messageId, result.prompt)
            is WizardResult.ShowCategoryList -> renderCategoryList(bot, chatId, messageId, result)
            is WizardResult.ShowTagList -> renderTagList(bot, chatId, messageId, result)
            is WizardResult.ShowPreview -> renderPreview(bot, chatId, messageId, result.session)
            is WizardResult.WizardComplete -> edit(bot, chatId, messageId, result.summary)
            is WizardResult.SessionExpired -> edit(bot, chatId, messageId, "Session expired. Use /new to start again.")
            is WizardResult.NoOp -> { /* nothing to render */ }
        }
    }

    /** Called when starting a new wizard — must send a fresh message and return its ID. */
    fun renderNewWizard(bot: TelegramLongPollingBot, chatId: Long): Message? {
        val keyboard = inlineKeyboard(listOf(
            listOf(button("Transfer", "t:transfer")),
            listOf(button("Withdrawal", "t:withdrawal")),
            listOf(button("Deposit", "t:deposit")),
        ))
        return sendMessage(bot, chatId, "Select transaction type:", keyboard)
    }

    // ── Private renderers ─────────────────────────────────────────────────────

    private fun renderTypeSelection(bot: TelegramLongPollingBot, chatId: Long, messageId: Int?) {
        val keyboard = inlineKeyboard(listOf(
            listOf(button("Transfer", "t:transfer")),
            listOf(button("Withdrawal", "t:withdrawal")),
            listOf(button("Deposit", "t:deposit")),
        ))
        edit(bot, chatId, messageId, "Select transaction type:", keyboard)
    }

    private fun renderAccountList(
        bot: TelegramLongPollingBot,
        chatId: Long,
        messageId: Int?,
        result: WizardResult.ShowAccountList,
    ) {
        val keyboard = buildAccountKeyboard(
            accounts = result.accounts,
            page = result.page,
            total = result.total,
            selectPrefix = result.selectPrefix,
            pagePrefix = result.pagePrefix,
        )
        edit(bot, chatId, messageId, result.prompt, keyboard)
    }

    private fun renderAccountSearch(
        bot: TelegramLongPollingBot,
        chatId: Long,
        messageId: Int?,
        result: WizardResult.ShowAccountSearch,
    ) {
        val (selectPrefix, pagePrefix, createCallback) = when (result.accountKind) {
            WizardResult.AccountKind.EXPENSE -> Triple("ea", "ea_p", "ea_new")
            WizardResult.AccountKind.REVENUE -> Triple("ra", "ra_p", "ra_new")
        }
        val start = result.page * pageSize
        val pageResults = result.results.drop(start).take(pageSize)
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        for (account in pageResults) {
            rows += listOf(button(account.name, "$selectPrefix:${account.id}"))
        }
        val navRow = mutableListOf<InlineKeyboardButton>()
        if (result.page > 0) navRow += button("← Prev", "$pagePrefix:${result.page - 1}")
        if (start + pageSize < result.total) navRow += button("Next →", "$pagePrefix:${result.page + 1}")
        if (navRow.isNotEmpty()) rows += navRow
        val kindLabel = if (result.accountKind == WizardResult.AccountKind.EXPENSE) "expense" else "revenue"
        rows += listOf(button("+ Create new $kindLabel account", createCallback))
        val prompt = if (result.total == 0) {
            "No accounts found for \"${result.query}\". You can create a new one:"
        } else {
            "Found ${result.total} account(s) for \"${result.query}\". Select or create new:"
        }
        edit(bot, chatId, messageId, prompt, inlineKeyboard(rows))
    }

    private fun renderCategoryList(
        bot: TelegramLongPollingBot,
        chatId: Long,
        messageId: Int?,
        result: WizardResult.ShowCategoryList,
    ) {
        val keyboard = buildCategoryKeyboard(result.categories, result.page, result.total)
        edit(bot, chatId, messageId, "Select a category:", keyboard)
    }

    private fun renderTagList(
        bot: TelegramLongPollingBot,
        chatId: Long,
        messageId: Int?,
        result: WizardResult.ShowTagList,
    ) {
        val rows = result.tags.chunked(2).map { row -> row.map { tag -> button(tag, "tg:$tag") } }
        edit(bot, chatId, messageId, "Select a tag:", inlineKeyboard(rows))
    }

    private fun renderPreview(bot: TelegramLongPollingBot, chatId: Long, messageId: Int?, session: WizardSession) {
        val tagLabel = if (session.tag != null) "Change tag" else "Add tag"
        val keyboard = inlineKeyboard(listOf(
            listOf(button("Change date/time", "pv:dt")),
            listOf(button(tagLabel, "pv:tag")),
            listOf(button("Submit", "pv:sub")),
        ))
        edit(bot, chatId, messageId, buildPreviewText(session), keyboard)
    }

    private fun buildPreviewText(session: WizardSession): String = buildString {
        appendLine("Transaction Preview")
        when (session.transactionType) {
            TransactionType.TRANSFER -> {
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
            TransactionType.WITHDRAWAL -> {
                appendLine("Type: Withdrawal")
                appendLine("From: ${session.sourceAccount?.name} (${session.sourceAccount?.currencyCode})")
                appendLine("To: ${session.destinationAccount?.name}")
                appendLine("Amount: ${session.amount} ${session.sourceAccount?.currencyCode}")
                appendLine("Category: ${session.category?.name ?: "-"}")
            }
            TransactionType.DEPOSIT -> {
                appendLine("Type: Deposit")
                appendLine("From: ${session.sourceAccount?.name}")
                appendLine("To: ${session.destinationAccount?.name} (${session.destinationAccount?.currencyCode})")
                appendLine("Amount: ${session.amount} ${session.destinationAccount?.currencyCode}")
                appendLine("Category: ${session.category?.name ?: "-"}")
            }
            null -> appendLine("Type: unknown")
        }
        appendLine("Date: ${session.dateTime.format(dtFormatter)}")
        append("Tag: ${session.tag ?: "(none)"}")
    }

    // ── Keyboard builders ─────────────────────────────────────────────────────

    private fun buildAccountKeyboard(
        accounts: List<Account>,
        page: Int,
        total: Int,
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
        if (start + pageSize < total) navRow += button("Next →", "$pagePrefix:${page + 1}")
        if (navRow.isNotEmpty()) rows += navRow
        return inlineKeyboard(rows)
    }

    private fun buildCategoryKeyboard(categories: List<Category>, page: Int, total: Int): InlineKeyboardMarkup {
        val start = page * pageSize
        val pageCats = categories.drop(start).take(pageSize)
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        for (cat in pageCats) {
            rows += listOf(button(cat.name, "cat:${cat.id}"))
        }
        val navRow = mutableListOf<InlineKeyboardButton>()
        if (page > 0) navRow += button("← Prev", "cat_p:${page - 1}")
        if (start + pageSize < total) navRow += button("Next →", "cat_p:${page + 1}")
        if (navRow.isNotEmpty()) rows += navRow
        return inlineKeyboard(rows)
    }

    // ── Telegram helpers ──────────────────────────────────────────────────────

    private fun inlineKeyboard(rows: List<List<InlineKeyboardButton>>): InlineKeyboardMarkup =
        InlineKeyboardMarkup.builder().keyboard(rows).build()

    private fun button(text: String, callbackData: String): InlineKeyboardButton =
        InlineKeyboardButton.builder().text(text).callbackData(callbackData).build()

    fun sendMessage(
        bot: TelegramLongPollingBot,
        chatId: Long,
        text: String,
        markup: InlineKeyboardMarkup? = null,
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

    private fun edit(
        bot: TelegramLongPollingBot,
        chatId: Long,
        messageId: Int?,
        text: String,
        markup: InlineKeyboardMarkup? = null,
    ) {
        if (messageId == null) {
            sendMessage(bot, chatId, text, markup)
            return
        }
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
}
