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

    // ── Preview field editing ─────────────────────────────────────────────────

    private fun driveToSameCurrencyPreview(): WizardResult {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        service.handleCallback(chatId, messageId, "da:2")
        return service.handleText(chatId, "150.00")
    }

    private fun driveToCrossCurrencyPreview(): WizardResult {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")  // USD
        service.handleCallback(chatId, messageId, "da:3")  // EUR
        service.handleText(chatId, "100.00")
        return service.handleText(chatId, "92.00")
    }

    @Test
    fun `pv-sa from preview returns source account list and sets SelectSourceAccount step`() {
        driveToSameCurrencyPreview()
        val result = service.handleCallback(chatId, messageId, "pv:sa")
        assertTrue(result is WizardResult.ShowAccountList)
        assertEquals("sa", (result as WizardResult.ShowAccountList).selectPrefix)
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.SelectSourceAccount)
    }

    @Test
    fun `pv-da from preview returns destination account list and sets SelectDestinationAccount step`() {
        driveToSameCurrencyPreview()
        val result = service.handleCallback(chatId, messageId, "pv:da")
        assertTrue(result is WizardResult.ShowAccountList)
        assertEquals("da", (result as WizardResult.ShowAccountList).selectPrefix)
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.SelectDestinationAccount)
    }

    @Test
    fun `pv-amt from same-currency preview prompts for new amount and sets EnterAmount step`() {
        driveToSameCurrencyPreview()
        val result = service.handleCallback(chatId, messageId, "pv:amt")
        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue((result as WizardResult.ShowTextPrompt).prompt.contains("USD"))
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.EnterAmount)
    }

    @Test
    fun `pv-amt then new amount returns preview directly for transfer`() {
        driveToSameCurrencyPreview()
        service.handleCallback(chatId, messageId, "pv:amt")
        val result = service.handleText(chatId, "200.00")
        assertTrue(result is WizardResult.ShowPreview)
        assertEquals("200.00", (result as WizardResult.ShowPreview).session.amount)
    }

    @Test
    fun `pv-samt from cross-currency preview prompts for source amount and sets EnterSourceAmount step`() {
        driveToCrossCurrencyPreview()
        val result = service.handleCallback(chatId, messageId, "pv:samt")
        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.EnterSourceAmount)
    }

    @Test
    fun `pv-samt then new source amount returns preview directly skipping dest amount re-entry`() {
        driveToCrossCurrencyPreview()
        service.handleCallback(chatId, messageId, "pv:samt")
        val result = service.handleText(chatId, "110.00")
        assertTrue(result is WizardResult.ShowPreview)
        val session = (result as WizardResult.ShowPreview).session
        assertEquals("110.00", session.sourceAmount)
        assertEquals("92.00", session.destAmount) // old dest amount preserved
    }

    @Test
    fun `pv-damt from cross-currency preview prompts for dest amount and sets EnterDestAmount step`() {
        driveToCrossCurrencyPreview()
        val result = service.handleCallback(chatId, messageId, "pv:damt")
        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue((result as WizardResult.ShowTextPrompt).prompt.contains("EUR"))
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.EnterDestAmount)
    }

    @Test
    fun `pv-damt then new dest amount returns preview`() {
        driveToCrossCurrencyPreview()
        service.handleCallback(chatId, messageId, "pv:damt")
        val result = service.handleText(chatId, "95.00")
        assertTrue(result is WizardResult.ShowPreview)
        assertEquals("95.00", (result as WizardResult.ShowPreview).session.destAmount)
    }

    @Test
    fun `pv-sa then selecting new source advances to destination account selection`() {
        driveToSameCurrencyPreview()
        service.handleCallback(chatId, messageId, "pv:sa")
        val result = service.handleCallback(chatId, messageId, "sa:2")
        assertTrue(result is WizardResult.ShowAccountList)
        assertEquals("da", (result as WizardResult.ShowAccountList).selectPrefix)
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.SelectDestinationAccount)
    }

    // ── Stale-amount clearing on account change ───────────────────────────────

    @Test
    fun `selecting new source clears all amount fields`() {
        driveToSameCurrencyPreview() // amount = "150.00"
        service.handleCallback(chatId, messageId, "pv:sa")
        service.handleCallback(chatId, messageId, "sa:2") // new source
        val session = sessionRepo.get(chatId)!!
        assertNull(session.amount)
        assertNull(session.sourceAmount)
        assertNull(session.destAmount)
    }

    @Test
    fun `selecting new destination clears all amount fields`() {
        driveToSameCurrencyPreview() // amount = "150.00"
        service.handleCallback(chatId, messageId, "pv:da")
        service.handleCallback(chatId, messageId, "da:2") // same dest — amounts cleared anyway
        val session = sessionRepo.get(chatId)!!
        assertNull(session.amount)
        assertNull(session.sourceAmount)
        assertNull(session.destAmount)
    }

    @Test
    fun `switching same-currency to cross-currency via pv-da clears amount and sets EnterSourceAmount`() {
        driveToSameCurrencyPreview() // USD→USD, amount="150.00"
        service.handleCallback(chatId, messageId, "pv:da")
        service.handleCallback(chatId, messageId, "da:3") // EUR — cross-currency
        val session = sessionRepo.get(chatId)!!
        assertTrue(session.step is WizardStep.EnterSourceAmount)
        assertNull(session.amount)
        assertNull(session.sourceAmount)
        assertNull(session.destAmount)
    }

    @Test
    fun `switching cross-currency to same-currency via pv-da clears source and dest amounts`() {
        driveToCrossCurrencyPreview() // USD→EUR, sourceAmount="100.00", destAmount="92.00"
        service.handleCallback(chatId, messageId, "pv:da")
        service.handleCallback(chatId, messageId, "da:2") // USD — same-currency
        val session = sessionRepo.get(chatId)!!
        assertTrue(session.step is WizardStep.EnterAmount)
        assertNull(session.amount)
        assertNull(session.sourceAmount)
        assertNull(session.destAmount)
    }

    @Test
    fun `pv-da then cross-currency dest forces fresh source and dest amount entry`() {
        driveToCrossCurrencyPreview() // destAmount="92.00"
        service.handleCallback(chatId, messageId, "pv:da")
        service.handleCallback(chatId, messageId, "da:3") // EUR again — amounts cleared
        service.handleText(chatId, "105.00") // new source amount
        // destAmount was cleared by Fix 1b, so shortcut must NOT trigger
        val session = sessionRepo.get(chatId)!!
        assertTrue(session.step is WizardStep.EnterDestAmount)
        assertEquals("105.00", session.sourceAmount)
        assertNull(session.destAmount)
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
