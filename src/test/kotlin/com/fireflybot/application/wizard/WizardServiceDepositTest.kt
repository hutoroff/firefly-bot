package com.fireflybot.application.wizard

import com.fireflybot.adapter.out.mock.InMemoryWizardSessionRepository
import com.fireflybot.application.port.out.AccountRepository
import com.fireflybot.application.port.out.CategoryRepository
import com.fireflybot.application.port.out.TransactionRepository
import com.fireflybot.domain.model.Account
import com.fireflybot.domain.model.Category
import com.fireflybot.domain.model.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WizardServiceDepositTest {

    private val chatId = 3L
    private val messageId = 30

    private val usdDestAccount = Account("dst1", "Checking USD", "USD")
    private val eurDestAccount = Account("dst2", "Euro Account", "EUR")
    private val assetAccounts = listOf(usdDestAccount, eurDestAccount)

    private val usdRevenueAccount = Account("rev1", "Employer Paycheck", "USD")
    private val eurRevenueAccount = Account("rev2", "Rental Income EUR", "EUR")
    private val revenueAccounts = listOf(usdRevenueAccount, eurRevenueAccount)

    private val categories = listOf(
        Category("cat1", "Salary"),
        Category("cat2", "Other Income"),
    )

    private val accountRepo = mockk<AccountRepository>()
    private val categoryRepo = mockk<CategoryRepository>()
    private val txRepo = mockk<TransactionRepository>()
    private val sessionRepo = InMemoryWizardSessionRepository()
    private val service = WizardService(
        sessionRepo, accountRepo, categoryRepo, txRepo, listOf("salary", "bonus"),
    )

    @BeforeEach
    fun setUp() {
        every { accountRepo.getAssetAccounts() } returns assetAccounts
        every { accountRepo.searchRevenueAccounts(any()) } returns revenueAccounts
        every { accountRepo.searchRevenueAccounts("employer") } returns listOf(usdRevenueAccount)
        every { accountRepo.searchRevenueAccounts("rental") } returns listOf(eurRevenueAccount)
        every { categoryRepo.getCategories() } returns categories
        every { txRepo.createTransaction(any()) } returns "tx-id"
    }

    // ── Type selection — deposit selects DESTINATION first ────────────────────

    @Test
    fun `deposit type selection returns destination account list`() {
        service.startNewWizard(chatId)
        val result = service.handleCallback(chatId, messageId, "t:deposit")

        assertTrue(result is WizardResult.ShowAccountList)
        val list = result as WizardResult.ShowAccountList
        assertEquals("da", list.selectPrefix)
        assertEquals("da_p", list.pagePrefix)
        assertEquals(2, list.total)
    }

    // ── Destination account ───────────────────────────────────────────────────

    @Test
    fun `selecting destination account for deposit prompts for revenue account query`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")

        val result = service.handleCallback(chatId, messageId, "da:dst1")

        assertTrue(result is WizardResult.ShowTextPrompt)
        val prompt = (result as WizardResult.ShowTextPrompt).prompt
        assertTrue(prompt.lowercase().contains("revenue"))
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.EnterRevenueAccountQuery)
        assertEquals("dst1", sessionRepo.get(chatId)!!.destinationAccount?.id)
    }

    @Test
    fun `destination page navigation for deposit returns filtered list`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")

        val result = service.handleCallback(chatId, messageId, "da_p:1")

        assertTrue(result is WizardResult.ShowAccountList)
        assertEquals(1, sessionRepo.get(chatId)!!.accountPage)
    }

    // ── Revenue account search ────────────────────────────────────────────────

    @Test
    fun `revenue account query returns search results with REVENUE kind`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst1")

        val result = service.handleText(chatId, "employer")

        assertTrue(result is WizardResult.ShowAccountSearch)
        val search = result as WizardResult.ShowAccountSearch
        assertEquals("employer", search.query)
        assertEquals(WizardResult.AccountKind.REVENUE, search.accountKind)
        assertEquals(1, search.results.size)
        assertEquals("rev1", search.results[0].id)
    }

    @Test
    fun `empty revenue account query re-prompts`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst1")

        val result = service.handleText(chatId, "  ")

        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue((result as WizardResult.ShowTextPrompt).prompt.lowercase().contains("enter"))
    }

    @Test
    fun `revenue account page navigation re-uses stored query`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst1")
        service.handleText(chatId, "employer")

        val result = service.handleCallback(chatId, messageId, "ra_p:1")

        assertTrue(result is WizardResult.ShowAccountSearch)
        assertEquals("employer", (result as WizardResult.ShowAccountSearch).query)
        assertEquals(1, sessionRepo.get(chatId)!!.accountPage)
    }

    // ── Currency match ────────────────────────────────────────────────────────

    @Test
    fun `selecting revenue account with matching currency moves to amount entry`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst1") // USD dest
        service.handleText(chatId, "employer") // USD revenue

        val result = service.handleCallback(chatId, messageId, "ra:rev1")

        assertTrue(result is WizardResult.ShowTextPrompt)
        val prompt = (result as WizardResult.ShowTextPrompt).prompt
        assertTrue(prompt.contains("USD"))
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.EnterAmount)
        // sourceAccount holds the revenue account in deposit flow
        assertEquals("rev1", sessionRepo.get(chatId)!!.sourceAccount?.id)
    }

    @Test
    fun `selecting revenue account with mismatched currency returns error`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst1") // USD dest
        service.handleText(chatId, "rental") // EUR revenue

        val result = service.handleCallback(chatId, messageId, "ra:rev2")

        assertTrue(result is WizardResult.ShowTextPrompt)
        val prompt = (result as WizardResult.ShowTextPrompt).prompt
        assertTrue(prompt.lowercase().contains("currency mismatch"))
        assertTrue(prompt.contains("USD"))
        assertTrue(prompt.contains("EUR"))
    }

    @Test
    fun `selecting revenue account not in search results returns NoOp`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst1")
        service.handleText(chatId, "employer")

        val result = service.handleCallback(chatId, messageId, "ra:nonexistent")
        assertTrue(result is WizardResult.NoOp)
    }

    @Test
    fun `ra with no stored query returns NoOp`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst1")
        // skip revenue query step — call ra: directly

        val result = service.handleCallback(chatId, messageId, "ra:rev1")
        assertTrue(result is WizardResult.NoOp)
    }

    // ── Create new revenue account ────────────────────────────────────────────

    @Test
    fun `ra_new prompts for new revenue account name`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst1")

        val result = service.handleCallback(chatId, messageId, "ra_new")

        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue((result as WizardResult.ShowTextPrompt).prompt.lowercase().contains("name"))
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.EnterNewRevenueAccountName)
    }

    @Test
    fun `entering new revenue account name creates account and moves to amount entry`() {
        val newAccount = Account("new456", "Client Payments", "USD")
        every { accountRepo.createRevenueAccount("Client Payments", "USD") } returns newAccount

        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst1") // USD dest
        service.handleCallback(chatId, messageId, "ra_new")

        val result = service.handleText(chatId, "Client Payments")

        assertTrue(result is WizardResult.ShowTextPrompt)
        val prompt = (result as WizardResult.ShowTextPrompt).prompt
        assertTrue(prompt.contains("USD"))
        val session = sessionRepo.get(chatId)!!
        assertTrue(session.step is WizardStep.EnterAmount)
        assertEquals("new456", session.sourceAccount?.id)
    }

    @Test
    fun `new revenue account name uses destination account currency`() {
        val newAccount = Account("newEur", "EU Client", "EUR")
        every { accountRepo.createRevenueAccount("EU Client", "EUR") } returns newAccount

        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst2") // EUR dest
        service.handleCallback(chatId, messageId, "ra_new")
        service.handleText(chatId, "EU Client")

        verify { accountRepo.createRevenueAccount("EU Client", "EUR") }
    }

    @Test
    fun `empty new revenue account name re-prompts`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst1")
        service.handleCallback(chatId, messageId, "ra_new")

        val result = service.handleText(chatId, "")

        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue((result as WizardResult.ShowTextPrompt).prompt.lowercase().contains("cannot be empty"))
    }

    // ── Amount + Category ─────────────────────────────────────────────────────

    @Test
    fun `valid amount for deposit shows category list`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst1")
        service.handleText(chatId, "employer")
        service.handleCallback(chatId, messageId, "ra:rev1")

        val result = service.handleText(chatId, "2000.00")

        assertTrue(result is WizardResult.ShowCategoryList)
        val catList = result as WizardResult.ShowCategoryList
        assertEquals(2, catList.total)
    }

    @Test
    fun `selecting category for deposit moves to preview`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst1")
        service.handleText(chatId, "employer")
        service.handleCallback(chatId, messageId, "ra:rev1")
        service.handleText(chatId, "2000.00")

        val result = service.handleCallback(chatId, messageId, "cat:cat1")

        assertTrue(result is WizardResult.ShowPreview)
        val session = (result as WizardResult.ShowPreview).session
        assertEquals("cat1", session.category?.id)
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    @Test
    fun `submit deposit creates Transaction-Deposit with all fields`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst1")
        service.handleText(chatId, "employer")
        service.handleCallback(chatId, messageId, "ra:rev1")
        service.handleText(chatId, "2000.00")
        service.handleCallback(chatId, messageId, "cat:cat1")

        val result = service.handleCallback(chatId, messageId, "pv:sub")

        assertTrue(result is WizardResult.WizardComplete)
        verify(exactly = 1) {
            txRepo.createTransaction(withArg { tx ->
                assertTrue(tx is Transaction.Deposit)
                val d = tx as Transaction.Deposit
                assertEquals("rev1", d.revenueAccount.id)
                assertEquals("dst1", d.destinationAccount.id)
                assertEquals("2000.00", d.amount)
                assertEquals("cat1", d.category?.id)
            })
        }
        assertNull(sessionRepo.get(chatId))
    }

    @Test
    fun `WizardComplete summary contains deposit success message`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst1")
        service.handleText(chatId, "employer")
        service.handleCallback(chatId, messageId, "ra:rev1")
        service.handleText(chatId, "2000.00")
        service.handleCallback(chatId, messageId, "cat:cat1")

        val result = service.handleCallback(chatId, messageId, "pv:sub") as WizardResult.WizardComplete
        assertTrue(result.summary.contains("Deposit submitted successfully!"))
    }

    @Test
    fun `deposit amount prompt uses destination account currency`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        service.handleCallback(chatId, messageId, "da:dst2") // EUR dest
        service.handleText(chatId, "rental")                 // EUR revenue

        val result = service.handleCallback(chatId, messageId, "ra:rev2") // currency match

        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue((result as WizardResult.ShowTextPrompt).prompt.contains("EUR"))
    }
}
