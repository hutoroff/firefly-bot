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

class WizardServiceTransferTest {

    private val chatId = 1L
    private val messageId = 10

    private val usdAccount1 = Account("1", "Checking USD", "USD")
    private val usdAccount2 = Account("2", "Savings USD", "USD")
    private val eurAccount = Account("3", "Euro Account", "EUR")
    private val allAccounts = listOf(usdAccount1, usdAccount2, eurAccount)

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
        every { accountRepo.getAssetAccounts() } returns allAccounts
        every { categoryRepo.getCategories() } returns listOf(Category("c1", "Food"))
        every { txRepo.createTransaction(any()) } returns "tx-id"
        every { tagRepo.getTags() } returns listOf(Tag("t1", "food"), Tag("t2", "transport"), Tag("t3", "travel"))
    }

    // ── Type selection ────────────────────────────────────────────────────────

    @Test
    fun `transfer type selection returns source account list`() {
        service.startNewWizard(chatId)
        val result = service.handleCallback(chatId, messageId, "t:transfer")

        assertTrue(result is WizardResult.ShowAccountList)
        val list = result as WizardResult.ShowAccountList
        assertEquals("sa", list.selectPrefix)
        assertEquals("sa_p", list.pagePrefix)
        assertEquals(3, list.total)
        assertEquals(allAccounts, list.accounts)
        assertEquals(0, list.page)
    }

    // ── Source account selection ──────────────────────────────────────────────

    @Test
    fun `selecting source account for transfer shows destination list without source`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")

        val result = service.handleCallback(chatId, messageId, "sa:1")

        assertTrue(result is WizardResult.ShowAccountList)
        val list = result as WizardResult.ShowAccountList
        assertEquals("da", list.selectPrefix)
        assertEquals(2, list.total)
        assertTrue(list.accounts.none { it.id == "1" })
    }

    @Test
    fun `selecting unknown source account id returns NoOp`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")

        val result = service.handleCallback(chatId, messageId, "sa:nonexistent")
        assertTrue(result is WizardResult.NoOp)
    }

    @Test
    fun `source account page pagination updates session and returns account list`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")

        val result = service.handleCallback(chatId, messageId, "sa_p:2")

        assertTrue(result is WizardResult.ShowAccountList)
        assertEquals(2, sessionRepo.get(chatId)!!.accountPage)
    }

    // ── Destination account selection — same currency ─────────────────────────

    @Test
    fun `same-currency destination selection prompts for single amount`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1") // USD

        val result = service.handleCallback(chatId, messageId, "da:2") // USD

        assertTrue(result is WizardResult.ShowTextPrompt)
        val prompt = (result as WizardResult.ShowTextPrompt).prompt
        assertTrue(prompt.contains("USD"))
        assertFalse(prompt.lowercase().contains("withdrawal"))
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.EnterAmount)
    }

    @Test
    fun `selecting same account as source and destination returns NoOp`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")

        val result = service.handleCallback(chatId, messageId, "da:1")
        assertTrue(result is WizardResult.NoOp)
    }

    @Test
    fun `selecting unknown destination account id returns NoOp`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")

        val result = service.handleCallback(chatId, messageId, "da:nonexistent")
        assertTrue(result is WizardResult.NoOp)
    }

    @Test
    fun `destination account page pagination updates session and returns account list`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")

        val result = service.handleCallback(chatId, messageId, "da_p:1")

        assertTrue(result is WizardResult.ShowAccountList)
        assertEquals(1, sessionRepo.get(chatId)!!.accountPage)
    }

    // ── Amount input — same currency ──────────────────────────────────────────

    @Test
    fun `valid amount input shows preview for same-currency transfer`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        service.handleCallback(chatId, messageId, "da:2")

        val result = service.handleText(chatId, "150.00")

        assertTrue(result is WizardResult.ShowPreview)
        val session = (result as WizardResult.ShowPreview).session
        assertEquals("150.00", session.amount)
        assertNull(session.sourceAmount)
        assertNull(session.destAmount)
    }

    @Test
    fun `invalid amount re-prompts with error message`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        service.handleCallback(chatId, messageId, "da:2")

        val result = service.handleText(chatId, "not-a-number")

        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue((result as WizardResult.ShowTextPrompt).prompt.lowercase().contains("invalid"))
    }

    @Test
    fun `zero amount re-prompts`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        service.handleCallback(chatId, messageId, "da:2")

        val result = service.handleText(chatId, "0")
        assertTrue(result is WizardResult.ShowTextPrompt)
    }

    @Test
    fun `negative amount re-prompts`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        service.handleCallback(chatId, messageId, "da:2")

        val result = service.handleText(chatId, "-100")
        assertTrue(result is WizardResult.ShowTextPrompt)
    }

    // ── Destination account selection — cross currency ────────────────────────

    @Test
    fun `cross-currency destination selection prompts for source withdrawal amount`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1") // USD

        val result = service.handleCallback(chatId, messageId, "da:3") // EUR

        assertTrue(result is WizardResult.ShowTextPrompt)
        val prompt = (result as WizardResult.ShowTextPrompt).prompt
        assertTrue(prompt.lowercase().contains("withdrawal"))
        assertTrue(prompt.contains("USD"))
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.EnterSourceAmount)
    }

    @Test
    fun `valid source amount input prompts for destination amount`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        service.handleCallback(chatId, messageId, "da:3") // EUR

        val result = service.handleText(chatId, "100.00")

        assertTrue(result is WizardResult.ShowTextPrompt)
        val prompt = (result as WizardResult.ShowTextPrompt).prompt
        assertTrue(prompt.contains("EUR"))
        assertEquals("100.00", sessionRepo.get(chatId)!!.sourceAmount)
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.EnterDestAmount)
    }

    @Test
    fun `invalid source amount re-prompts`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        service.handleCallback(chatId, messageId, "da:3")

        val result = service.handleText(chatId, "abc")
        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue((result as WizardResult.ShowTextPrompt).prompt.lowercase().contains("invalid"))
    }

    @Test
    fun `valid dest amount shows preview for cross-currency transfer`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        service.handleCallback(chatId, messageId, "da:3")
        service.handleText(chatId, "100.00") // source amount

        val result = service.handleText(chatId, "92.00")

        assertTrue(result is WizardResult.ShowPreview)
        val session = (result as WizardResult.ShowPreview).session
        assertEquals("100.00", session.sourceAmount)
        assertEquals("92.00", session.destAmount)
        assertNull(session.amount)
    }

    @Test
    fun `invalid dest amount re-prompts`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        service.handleCallback(chatId, messageId, "da:3")
        service.handleText(chatId, "100.00")

        val result = service.handleText(chatId, "zero")
        assertTrue(result is WizardResult.ShowTextPrompt)
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    @Test
    fun `submit same-currency transfer creates Transaction-Transfer and returns WizardComplete`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        service.handleCallback(chatId, messageId, "da:2")
        service.handleText(chatId, "150.00")

        val result = service.handleCallback(chatId, messageId, "pv:sub")

        assertTrue(result is WizardResult.WizardComplete)
        verify(exactly = 1) {
            txRepo.createTransaction(withArg { tx ->
                assertTrue(tx is Transaction.Transfer)
                val t = tx as Transaction.Transfer
                assertEquals("1", t.sourceAccount.id)
                assertEquals("2", t.destinationAccount.id)
                assertEquals("150.00", t.amount)
                assertNull(t.sourceAmount)
                assertNull(t.destAmount)
            })
        }
        assertNull(sessionRepo.get(chatId))
    }

    @Test
    fun `submit cross-currency transfer creates Transaction-Transfer with source and dest amounts`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        service.handleCallback(chatId, messageId, "da:3")
        service.handleText(chatId, "100.00")
        service.handleText(chatId, "92.00")

        val result = service.handleCallback(chatId, messageId, "pv:sub")

        assertTrue(result is WizardResult.WizardComplete)
        verify(exactly = 1) {
            txRepo.createTransaction(withArg { tx ->
                val t = tx as Transaction.Transfer
                assertNull(t.amount)
                assertEquals("100.00", t.sourceAmount)
                assertEquals("92.00", t.destAmount)
            })
        }
        assertNull(sessionRepo.get(chatId))
    }

    @Test
    fun `WizardComplete text contains transfer success message`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        service.handleCallback(chatId, messageId, "da:2")
        service.handleText(chatId, "150.00")

        val result = service.handleCallback(chatId, messageId, "pv:sub") as WizardResult.WizardComplete
        assertTrue(result.summary.contains("Transfer submitted successfully!"))
    }

    @Test
    fun `submit with tag preserves tag in created transaction`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        service.handleCallback(chatId, messageId, "da:2")
        service.handleText(chatId, "150.00")
        service.handleCallback(chatId, messageId, "tg:t3") // id for "travel"

        service.handleCallback(chatId, messageId, "pv:sub")

        verify {
            txRepo.createTransaction(withArg { tx ->
                assertEquals("travel", (tx as Transaction.Transfer).tag)
            })
        }
    }
}
