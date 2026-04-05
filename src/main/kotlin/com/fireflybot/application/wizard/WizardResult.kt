package com.fireflybot.application.wizard

import com.fireflybot.domain.model.Account
import com.fireflybot.domain.model.Category
import com.fireflybot.domain.model.Tag

sealed class WizardResult {

    /** Show the initial transaction-type selection. */
    data object ShowTypeSelection : WizardResult()

    /** Show a paginated list of asset accounts (source or destination selection). */
    data class ShowAccountList(
        val prompt: String,
        val accounts: List<Account>,
        val page: Int,
        val total: Int,
        val selectPrefix: String,
        val pagePrefix: String,
    ) : WizardResult()

    /** Show paginated search results for an expense or revenue account. */
    data class ShowAccountSearch(
        val query: String,
        val results: List<Account>,
        val page: Int,
        val total: Int,
        val accountKind: AccountKind,
    ) : WizardResult()

    /** Show a free-text prompt (amount input, search query, account name, date/time). */
    data class ShowTextPrompt(val prompt: String) : WizardResult()

    /** Show a paginated list of categories. */
    data class ShowCategoryList(
        val categories: List<Category>,
        val page: Int,
        val total: Int,
    ) : WizardResult()

    /** Show the tag selection grid. */
    data class ShowTagList(val tags: List<Tag>) : WizardResult()

    /** Show the transaction preview screen. */
    data class ShowPreview(val session: WizardSession) : WizardResult()

    /** Wizard completed — display a success summary. */
    data class WizardComplete(val summary: String) : WizardResult()

    /** The session for this chat no longer exists. */
    data class SessionExpired(val chatId: Long) : WizardResult()

    /** Nothing to render (e.g. unrecognised callback prefix). */
    data object NoOp : WizardResult()

    enum class AccountKind { EXPENSE, REVENUE }
}
