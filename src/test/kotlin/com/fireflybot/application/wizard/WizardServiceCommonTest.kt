package com.fireflybot.application.wizard

import com.fireflybot.adapter.out.persistence.JsonFileWizardSessionRepository
import com.fireflybot.application.port.out.AccountRepository
import com.fireflybot.application.port.out.CategoryRepository
import com.fireflybot.application.port.out.TagRepository
import com.fireflybot.application.port.out.TransactionRepository
import com.fireflybot.domain.model.Account
import com.fireflybot.domain.model.Category
import com.fireflybot.domain.model.Tag
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.Executor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Tests for session lifecycle, common callback routing, date/time input, and tag selection.
 */
class WizardServiceCommonTest {

    private val chatId = 42L
    private val messageId = 7

    private val accounts = listOf(
        Account("1", "Checking", "USD"),
        Account("2", "Savings", "USD"),
    )
    private val categories = listOf(Category("c1", "Food"))

    @TempDir
    lateinit var tempDir: Path

    private val accountRepo = mockk<AccountRepository>()
    private val categoryRepo = mockk<CategoryRepository>()
    private val txRepo = mockk<TransactionRepository>()
    private val tagRepo = mockk<TagRepository>()
    private lateinit var sessionRepo: JsonFileWizardSessionRepository
    private lateinit var service: WizardService

    @BeforeEach
    fun setUp() {
        sessionRepo = JsonFileWizardSessionRepository(tempDir.resolve("sessions.json").toString())
        service = WizardService(sessionRepo, accountRepo, categoryRepo, txRepo, tagRepo,
            executor = Executor { it.run() })
        every { accountRepo.getAssetAccounts() } returns accounts
        every { categoryRepo.getCategories() } returns categories
        every { txRepo.createTransaction(any()) } returns "tx-id"
        every { tagRepo.getTags() } returns listOf(Tag("1", "food"), Tag("2", "transport"), Tag("3", "utilities"))
    }

    // ── Cache preloads ────────────────────────────────────────────────────────

    @Test
    fun `startNewWizard preloads asset accounts`() {
        service.startNewWizard(chatId)
        verify(atLeast = 1) { accountRepo.getAssetAccounts() }
    }

    @Test
    fun `type selection preloads tags for all transaction types`() {
        for (type in listOf("transfer", "withdrawal", "deposit")) {
            service.startNewWizard(chatId)
            service.handleCallback(chatId, messageId, "t:$type")
            verify(atLeast = 1) { tagRepo.getTags() }
        }
    }

    @Test
    fun `type selection preloads categories for withdrawal`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:withdrawal")
        verify(atLeast = 1) { categoryRepo.getCategories() }
    }

    @Test
    fun `type selection preloads categories for deposit`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:deposit")
        verify(atLeast = 1) { categoryRepo.getCategories() }
    }

    @Test
    fun `type selection does not preload categories for transfer`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        verify(exactly = 0) { categoryRepo.getCategories() }
    }

    // ── Session lifecycle ────────────────────────────────────────────────────

    @Test
    fun `startNewWizard creates session at SelectType step`() {
        service.startNewWizard(chatId)
        val session = sessionRepo.get(chatId)
        assertNotNull(session)
        assertTrue(session!!.step is WizardStep.SelectType)
        assertNull(session.transactionType)
    }

    @Test
    fun `setWizardMessageId stores message id in existing session`() {
        service.startNewWizard(chatId)
        service.setWizardMessageId(chatId, 99)
        assertEquals(99, sessionRepo.get(chatId)!!.wizardMessageId)
    }

    @Test
    fun `setWizardMessageId is no-op when session does not exist`() {
        service.setWizardMessageId(chatId, 99)
        assertNull(sessionRepo.get(chatId))
    }

    @Test
    fun `getWizardMessageId returns null when no session exists`() {
        assertNull(service.getWizardMessageId(chatId))
    }

    @Test
    fun `getWizardMessageId returns stored message id`() {
        service.startNewWizard(chatId)
        service.setWizardMessageId(chatId, 55)
        assertEquals(55, service.getWizardMessageId(chatId))
    }

    // ── No-session guard ─────────────────────────────────────────────────────

    @Test
    fun `handleCallback returns SessionExpired when no session`() {
        val result = service.handleCallback(chatId, messageId, "t:transfer")
        assertTrue(result is WizardResult.SessionExpired)
        assertEquals(chatId, (result as WizardResult.SessionExpired).chatId)
    }

    @Test
    fun `handleText returns NoOp when no session`() {
        val result = service.handleText(chatId, "hello")
        assertTrue(result is WizardResult.NoOp)
    }

    // ── Message ID sync ──────────────────────────────────────────────────────

    @Test
    fun `handleCallback stores message id in session when it differs`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, 999, "t:transfer")
        assertEquals(999, sessionRepo.get(chatId)!!.wizardMessageId)
    }

    // ── Unknown callback prefix ──────────────────────────────────────────────

    @Test
    fun `handleCallback with unknown prefix returns NoOp`() {
        service.startNewWizard(chatId)
        val result = service.handleCallback(chatId, messageId, "unknown:data")
        assertTrue(result is WizardResult.NoOp)
    }

    @Test
    fun `handleCallback with empty data returns NoOp`() {
        service.startNewWizard(chatId)
        val result = service.handleCallback(chatId, messageId, "")
        assertTrue(result is WizardResult.NoOp)
    }

    // ── Malformed pagination callbacks ───────────────────────────────────────

    @Test
    fun `sa_p with non-integer page returns NoOp`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        val result = service.handleCallback(chatId, messageId, "sa_p:notanumber")
        assertTrue(result is WizardResult.NoOp)
    }

    @Test
    fun `da_p with non-integer page returns NoOp`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        val result = service.handleCallback(chatId, messageId, "da_p:notanumber")
        assertTrue(result is WizardResult.NoOp)
    }

    // ── Invalid type selection ────────────────────────────────────────────────

    @Test
    fun `type selection with unknown type returns text prompt and deletes session`() {
        service.startNewWizard(chatId)
        val result = service.handleCallback(chatId, messageId, "t:unsupported")
        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue((result as WizardResult.ShowTextPrompt).prompt.contains("not yet supported"))
        assertNull(sessionRepo.get(chatId))
    }

    // ── handleText on non-input steps returns NoOp ────────────────────────────

    @Test
    fun `handleText on SelectType step returns NoOp`() {
        service.startNewWizard(chatId) // step = SelectType
        val result = service.handleText(chatId, "hello")
        assertTrue(result is WizardResult.NoOp)
    }

    @Test
    fun `handleText on SelectSourceAccount step returns NoOp`() {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer") // step = SelectSourceAccount
        val result = service.handleText(chatId, "hello")
        assertTrue(result is WizardResult.NoOp)
    }

    // ── Preview actions ───────────────────────────────────────────────────────

    private fun driveToPreviewTransfer(): WizardResult {
        service.startNewWizard(chatId)
        service.handleCallback(chatId, messageId, "t:transfer")
        service.handleCallback(chatId, messageId, "sa:1")
        service.handleCallback(chatId, messageId, "da:2")
        return service.handleText(chatId, "100.00")
    }

    @Test
    fun `pv-dt action transitions to EnterDateTime step and returns date prompt`() {
        driveToPreviewTransfer()
        val result = service.handleCallback(chatId, messageId, "pv:dt")
        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue((result as WizardResult.ShowTextPrompt).prompt.contains("DD.MM.YYYY"))
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.EnterDateTime)
    }

    @Test
    fun `pv-tag action transitions to SelectTag step and returns tag list`() {
        driveToPreviewTransfer()
        val result = service.handleCallback(chatId, messageId, "pv:tag")
        assertTrue(result is WizardResult.ShowTagList)
        val tagList = result as WizardResult.ShowTagList
        assertTrue(tagList.tags.any { it.name == "food" })
        assertTrue(tagList.tags.any { it.name == "transport" })
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.SelectTag)
    }

    @Test
    fun `pv-unknown returns NoOp`() {
        driveToPreviewTransfer()
        val result = service.handleCallback(chatId, messageId, "pv:unknown")
        assertTrue(result is WizardResult.NoOp)
    }

    // ── DateTime input ────────────────────────────────────────────────────────

    @Test
    fun `valid datetime input updates session and returns preview`() {
        driveToPreviewTransfer()
        service.handleCallback(chatId, messageId, "pv:dt")

        val result = service.handleText(chatId, "20.06.2025 14:30")
        assertTrue(result is WizardResult.ShowPreview)
        val session = (result as WizardResult.ShowPreview).session
        assertEquals(2025, session.dateTime.year)
        assertEquals(6, session.dateTime.monthValue)
        assertEquals(20, session.dateTime.dayOfMonth)
        assertEquals(14, session.dateTime.hour)
        assertEquals(30, session.dateTime.minute)
        assertTrue(session.step is WizardStep.Preview)
    }

    @Test
    fun `invalid datetime format returns error prompt`() {
        driveToPreviewTransfer()
        service.handleCallback(chatId, messageId, "pv:dt")

        val result = service.handleText(chatId, "2025-06-20 14:30")
        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue((result as WizardResult.ShowTextPrompt).prompt.contains("Invalid format"))
    }

    @Test
    fun `completely wrong datetime text returns error prompt`() {
        driveToPreviewTransfer()
        service.handleCallback(chatId, messageId, "pv:dt")

        val result = service.handleText(chatId, "not a date")
        assertTrue(result is WizardResult.ShowTextPrompt)
    }

    // ── Tag selection ─────────────────────────────────────────────────────────

    @Test
    fun `tag selection resolves id to name and returns preview`() {
        driveToPreviewTransfer()
        service.handleCallback(chatId, messageId, "pv:tag")

        val result = service.handleCallback(chatId, messageId, "tg:1") // id for "food"
        assertTrue(result is WizardResult.ShowPreview)
        val session = (result as WizardResult.ShowPreview).session
        assertEquals("food", session.tag)
        assertTrue(session.step is WizardStep.Preview)
    }

    @Test
    fun `tag selection with unknown id returns NoOp`() {
        driveToPreviewTransfer()
        service.handleCallback(chatId, messageId, "pv:tag")
        val result = service.handleCallback(chatId, messageId, "tg:unknown-id")
        assertTrue(result is WizardResult.NoOp)
    }

    // ── Description input ─────────────────────────────────────────────────────

    @Test
    fun `pv-desc action transitions to EnterDescription step and returns text prompt`() {
        driveToPreviewTransfer()
        val result = service.handleCallback(chatId, messageId, "pv:desc")
        assertTrue(result is WizardResult.ShowTextPrompt)
        assertTrue(sessionRepo.get(chatId)!!.step is WizardStep.EnterDescription)
    }

    @Test
    fun `description input saves description and returns preview`() {
        driveToPreviewTransfer()
        service.handleCallback(chatId, messageId, "pv:desc")

        val result = service.handleText(chatId, "My custom note")
        assertTrue(result is WizardResult.ShowPreview)
        val session = (result as WizardResult.ShowPreview).session
        assertEquals("My custom note", session.description)
        assertTrue(session.step is WizardStep.Preview)
    }

    @Test
    fun `description input trims whitespace`() {
        driveToPreviewTransfer()
        service.handleCallback(chatId, messageId, "pv:desc")

        val result = service.handleText(chatId, "  trimmed  ")
        assertTrue(result is WizardResult.ShowPreview)
        assertEquals("trimmed", (result as WizardResult.ShowPreview).session.description)
    }

    @Test
    fun `blank description input clears description and returns preview`() {
        driveToPreviewTransfer()
        service.handleCallback(chatId, messageId, "pv:desc")
        service.handleText(chatId, "initial note")
        service.handleCallback(chatId, messageId, "pv:desc")

        val result = service.handleText(chatId, "   ")
        assertTrue(result is WizardResult.ShowPreview)
        assertNull((result as WizardResult.ShowPreview).session.description)
    }
}
