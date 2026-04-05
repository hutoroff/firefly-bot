package com.fireflybot.application.wizard

import com.fireflybot.adapter.out.mock.InMemoryWizardSessionRepository
import com.fireflybot.application.port.out.AccountRepository
import com.fireflybot.application.port.out.CategoryRepository
import com.fireflybot.application.port.out.TagRepository
import com.fireflybot.application.port.out.TransactionRepository
import com.fireflybot.domain.model.Account
import com.fireflybot.domain.model.Category
import com.fireflybot.domain.model.Tag
import com.fireflybot.domain.model.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WizardServiceWithdrawalTest {

    private val chatId = 2L
    private val messageId = 20

    private val usdSourceAccount = Account("src1", "Checking USD", "USD")
    private val eurSourceAccount = Account("src2", "Euro Account", "EUR")
    private val assetAccounts = listOf(usdSourceAccount, eurSourceAccount)

    private val usdExpenseAccount = Account("exp1", "Grocery Store", "USD")
    private val eurExpenseAccount = Account("exp2", "French Restaurant", "EUR")
    private val expenseAccounts = listOf(usdExpenseAccount, eurExpenseAccount)

    private val categories = listOf(
        Category("cat1", "Food & Dining"),
        Category("cat2", "Transport"),
    )

    private val accountRepo = mockk<AccountRepository>()
    private val categoryRepo = mockk<CategoryRepository>()
    private val txRepo = mockk<TransactionRepository>()
    private val tagRepo = mockk<TagRepository>()
    private val sessionRepo = InMemoryWizardSessionRepository()
    private val service = WizardService(
        sessionRepo, accountRepo, categoryRepo, txRepo, tagRepo,
    )

    @BeforeEach
    fun setUp() {
        every { accountRepo.getAssetAccounts() } returns assetAccounts
        every { accountRepo.searchExpenseAccounts(any()) } returns expenseAccounts
        every { accountRepo.searchExpenseAccounts("grocery") } returns listOf(usdExpenseAccount)
        every { accountRepo.searchExpenseAccounts("french") } returns listOf(eurExpenseAccount)
        every { accountRepo.searchExpenseAccounts("") } returns expenseAccounts
        every { categoryRepo.getCategories() } returns categories
        every { txRepo.createTransaction(any()) } returns "tx-id"
        every { tagRepo.getTags() } returns listOf(Tag("1", "food"), Tag("2", "transport"))
    }

    // ── Type selection ────────────────────────────────────────────────────────

    @Test
    fun `withdrawal type selection returns source account list`() {
        service.startNewWizard(chatId)
        val result = service.handleCallback(chatId, messageId, "t:withdrawal")

        assertTrue(result is WizardResult.ShowAccountList)
        val list = result as WizardResult.ShowAccountList
        assertEquals("sa", list.selectPrefix)
        assertEquals(2, list.total)
    }

    // ── Source account ────────────────────────────────────────────────────────

    @Test
    fun `selecting source account for withdrawal prompts for expense account query`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")

        val result = service.handleCallback(chatId, messageId, "sa:src1")

        assertTrue(result is WizardResult.ShowTextPrompt)
        val prompt = (result as WizardResult.ShowTextPrompt).prompt
        assertTrue(prompt.lowercase().contains("expense"))
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.EnterExpenseAccountQuery)
    }

    // ── Expense account search ────────────────────────────────────────────────

    @Test
    fun `expense account query returns search results`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1")

        val result = service.handleText(chatId, "grocery")

        assertTrue(result is WizardResult.ShowAccountSearch)
        val search = result as WizardResult.ShowAccountSearch
        assertEquals("grocery", search.query)
        assertEquals(WizardResult.AccountKind.EXPENSE, search.accountKind)
        assertEquals(1, search.results.size)
        assertEquals("exp1", search.results[0].id)
    }

    @Test
    fun `empty expense account query re-prompts without searching`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1")

        val result = service.handleText(chatId, "   ")

        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue((result as WizardResult.ShowTextPrompt).prompt.lowercase().contains("enter"))
    }

    @Test
    fun `expense account page navigation re-uses stored query`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1")
        service.handleText(chatId, "grocery")

        val result = service.handleCallback(chatId, messageId, "ea_p:1")

        assertTrue(result is WizardResult.ShowAccountSearch)
        assertEquals("grocery", (result as WizardResult.ShowAccountSearch).query)
        assertEquals(1, sessionRepo.get(chatId)!!.accountPage)
    }

    // ── Currency match ────────────────────────────────────────────────────────

    @Test
    fun `selecting expense account with matching currency moves to amount entry`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1") // USD
        service.handleText(chatId, "grocery") // matches usdExpenseAccount (USD)

        val result = service.handleCallback(chatId, messageId, "ea:exp1")

        assertTrue(result is WizardResult.ShowTextPrompt)
        val prompt = (result as WizardResult.ShowTextPrompt).prompt
        assertTrue(prompt.contains("USD"))
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.EnterAmount)
    }

    @Test
    fun `selecting expense account with mismatched currency returns error and stays at query`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1") // USD
        service.handleText(chatId, "french") // matches eurExpenseAccount (EUR)

        val result = service.handleCallback(chatId, messageId, "ea:exp2")

        assertTrue(result is WizardResult.ShowTextPrompt)
        val prompt = (result as WizardResult.ShowTextPrompt).prompt
        assertTrue(prompt.lowercase().contains("currency mismatch"))
        assertTrue(prompt.contains("USD"))
        assertTrue(prompt.contains("EUR"))
    }

    @Test
    fun `selecting expense account not in search results returns NoOp`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1")
        service.handleText(chatId, "grocery")

        val result = service.handleCallback(chatId, messageId, "ea:nonexistent")
        assertTrue(result is WizardResult.NoOp)
    }

    @Test
    fun `ea with no stored query returns NoOp`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1")
        // skipping expense query step — call ea: directly

        val result = service.handleCallback(chatId, messageId, "ea:exp1")
        assertTrue(result is WizardResult.NoOp)
    }

    // ── Create new expense account ────────────────────────────────────────────

    @Test
    fun `ea_new prompts for new expense account name`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1")

        val result = service.handleCallback(chatId, messageId, "ea_new")

        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue((result as WizardResult.ShowTextPrompt).prompt.lowercase().contains("name"))
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.EnterNewExpenseAccountName)
    }

    @Test
    fun `entering new expense account name creates account and moves to amount entry`() {
        val newAccount = Account("new123", "My New Shop", "USD")
        every { accountRepo.createExpenseAccount("My New Shop", "USD") } returns newAccount

        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1") // USD source
        service.handleCallback(chatId, messageId, "ea_new")

        val result = service.handleText(chatId, "My New Shop")

        assertTrue(result is WizardResult.ShowTextPrompt)
        val prompt = (result as WizardResult.ShowTextPrompt).prompt
        assertTrue(prompt.contains("USD"))
        val session = sessionRepo.get(chatId)!!
        assertTrue(session.step is WizardStep.EnterAmount)
        assertEquals("new123", session.destinationAccount?.id)
    }

    @Test
    fun `new expense account name uses source account currency`() {
        val newAccount = Account("new123", "Shop EUR", "EUR")
        every { accountRepo.createExpenseAccount("Shop EUR", "EUR") } returns newAccount

        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src2") // EUR source
        service.handleCallback(chatId, messageId, "ea_new")
        service.handleText(chatId, "Shop EUR")

        verify { accountRepo.createExpenseAccount("Shop EUR", "EUR") }
    }

    @Test
    fun `empty new expense account name re-prompts`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1")
        service.handleCallback(chatId, messageId, "ea_new")

        val result = service.handleText(chatId, "   ")

        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue((result as WizardResult.ShowTextPrompt).prompt.lowercase().contains("cannot be empty"))
    }

    // ── Amount + Category ─────────────────────────────────────────────────────

    @Test
    fun `valid amount for withdrawal shows category list`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1")
        service.handleText(chatId, "grocery")
        service.handleCallback(chatId, messageId, "ea:exp1")

        val result = service.handleText(chatId, "55.00")

        assertTrue(result is WizardResult.ShowCategoryList)
        val catList = result as WizardResult.ShowCategoryList
        assertEquals(2, catList.total)
        assertTrue(catList.categories.any { it.id == "cat1" })
    }

    @Test
    fun `category page navigation returns updated category list`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1")
        service.handleText(chatId, "grocery")
        service.handleCallback(chatId, messageId, "ea:exp1")
        service.handleText(chatId, "55.00")

        val result = service.handleCallback(chatId, messageId, "cat_p:1")

        assertTrue(result is WizardResult.ShowCategoryList)
        assertEquals(1, sessionRepo.get(chatId)!!.accountPage)
    }

    @Test
    fun `selecting category moves to preview`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1")
        service.handleText(chatId, "grocery")
        service.handleCallback(chatId, messageId, "ea:exp1")
        service.handleText(chatId, "55.00")

        val result = service.handleCallback(chatId, messageId, "cat:cat1")

        assertTrue(result is WizardResult.ShowPreview)
        val session = (result as WizardResult.ShowPreview).session
        assertEquals("cat1", session.category?.id)
        assertEquals("Food & Dining", session.category?.name)
        assertTrue(session.step is WizardStep.Preview)
    }

    @Test
    fun `selecting unknown category id returns NoOp`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1")
        service.handleText(chatId, "grocery")
        service.handleCallback(chatId, messageId, "ea:exp1")
        service.handleText(chatId, "55.00")

        val result = service.handleCallback(chatId, messageId, "cat:nonexistent")
        assertTrue(result is WizardResult.NoOp)
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    @Test
    fun `submit withdrawal creates Transaction-Withdrawal with all fields`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1")
        service.handleText(chatId, "grocery")
        service.handleCallback(chatId, messageId, "ea:exp1")
        service.handleText(chatId, "55.00")
        service.handleCallback(chatId, messageId, "cat:cat1")

        val result = service.handleCallback(chatId, messageId, "pv:sub")

        assertTrue(result is WizardResult.WizardComplete)
        verify(exactly = 1) {
            txRepo.createTransaction(withArg { tx ->
                assertTrue(tx is Transaction.Withdrawal)
                val w = tx as Transaction.Withdrawal
                assertEquals("src1", w.sourceAccount.id)
                assertEquals("exp1", w.expenseAccount.id)
                assertEquals("55.00", w.amount)
                assertEquals("cat1", w.category?.id)
            })
        }
        assertNull(sessionRepo.get(chatId))
    }

    @Test
    fun `submit from non-Preview step returns NoOp`() {
        // pv:sub is step-guarded: submitting while still on SelectCategory (i.e. before
        // the user has seen and confirmed the Preview) must be rejected.
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1")
        service.handleText(chatId, "grocery")
        service.handleCallback(chatId, messageId, "ea:exp1")
        service.handleText(chatId, "55.00") // step is now SelectCategory, not Preview

        val result = service.handleCallback(chatId, messageId, "pv:sub")

        assertTrue(result is WizardResult.NoOp)
        verify(exactly = 0) { txRepo.createTransaction(any()) }
    }

    @Test
    fun `WizardComplete summary contains withdrawal success message`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        service.handleCallback(chatId, messageId, "sa:src1")
        service.handleText(chatId, "grocery")
        service.handleCallback(chatId, messageId, "ea:exp1")
        service.handleText(chatId, "55.00")
        service.handleCallback(chatId, messageId, "cat:cat1")

        val result = service.handleCallback(chatId, messageId, "pv:sub") as WizardResult.WizardComplete
        assertTrue(result.summary.contains("Withdrawal submitted successfully!"))
    }
}
