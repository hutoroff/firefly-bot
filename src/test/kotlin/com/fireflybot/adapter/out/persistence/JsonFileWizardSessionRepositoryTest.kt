package com.fireflybot.adapter.out.persistence

import com.fireflybot.application.wizard.WizardSession
import com.fireflybot.application.wizard.WizardStep
import com.fireflybot.domain.model.Account
import com.fireflybot.domain.model.Category
import com.fireflybot.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class JsonFileWizardSessionRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    private fun repo(): JsonFileWizardSessionRepository =
        JsonFileWizardSessionRepository(tempDir.resolve("sessions.json").toString())

    private fun session(chatId: Long = 1L) = WizardSession(chatId = chatId)

    // ── Basic contract ────────────────────────────────────────────────────────

    @Test
    fun `get returns null when file does not exist`() {
        assertNull(repo().get(1L))
    }

    @Test
    fun `get returns null when session not stored`() {
        val r = repo()
        r.save(session(2L))
        assertNull(r.get(1L))
    }

    @Test
    fun `save and get round-trips minimal session`() {
        val r = repo()
        val s = session(10L)
        r.save(s)
        assertEquals(s, r.get(10L))
    }

    @Test
    fun `save overwrites existing session`() {
        val r = repo()
        r.save(session(10L))
        val updated = WizardSession(chatId = 10L, step = WizardStep.EnterAmount)
        r.save(updated)
        assertEquals(WizardStep.EnterAmount, r.get(10L)!!.step)
    }

    @Test
    fun `delete removes stored session`() {
        val r = repo()
        r.save(session(10L))
        r.delete(10L)
        assertNull(r.get(10L))
    }

    @Test
    fun `delete is no-op when session does not exist`() {
        val r = repo()
        r.delete(99L)
        assertNull(r.get(99L))
    }

    @Test
    fun `sessions for different chats are isolated`() {
        val r = repo()
        r.save(session(1L))
        r.save(WizardSession(chatId = 2L, step = WizardStep.EnterAmount))
        assertEquals(WizardStep.SelectType, r.get(1L)!!.step)
        assertEquals(WizardStep.EnterAmount, r.get(2L)!!.step)
    }

    @Test
    fun `deleting one chat session does not affect another`() {
        val r = repo()
        r.save(session(1L))
        r.save(session(2L))
        r.delete(1L)
        assertNull(r.get(1L))
        assertNotNull(r.get(2L))
    }

    // ── Persistence across instances ──────────────────────────────────────────

    @Test
    fun `session persists when new repository instance reads the same file`() {
        val path = tempDir.resolve("sessions.json").toString()
        val r1 = JsonFileWizardSessionRepository(path)
        r1.save(WizardSession(chatId = 42L, step = WizardStep.Preview))

        val r2 = JsonFileWizardSessionRepository(path)
        val loaded = r2.get(42L)
        assertNotNull(loaded)
        assertEquals(WizardStep.Preview, loaded!!.step)
    }

    @Test
    fun `delete persists when new repository instance reads the same file`() {
        val path = tempDir.resolve("sessions.json").toString()
        val r1 = JsonFileWizardSessionRepository(path)
        r1.save(session(5L))
        r1.delete(5L)

        val r2 = JsonFileWizardSessionRepository(path)
        assertNull(r2.get(5L))
    }

    // ── Full session round-trip ───────────────────────────────────────────────

    @Test
    fun `round-trip preserves all non-null fields`() {
        val r = repo()
        val fixedTime = LocalDateTime.of(2024, 6, 15, 14, 30, 0)
        val full = WizardSession(
            chatId = 7L,
            step = WizardStep.Preview,
            transactionType = TransactionType.WITHDRAWAL,
            sourceAccount = Account("acc-1", "Checking", "EUR"),
            destinationAccount = Account("acc-2", "Savings", "USD"),
            amount = "123.45",
            sourceAmount = "100.00",
            destAmount = "110.00",
            dateTime = fixedTime,
            tag = "groceries",
            category = Category("cat-1", "Food"),
            expenseAccountQuery = "super",
            revenueAccountQuery = "salary",
            accountPage = 3,
            wizardMessageId = 999,
        )
        r.save(full)
        assertEquals(full, r.get(7L))
    }

    @Test
    fun `round-trip preserves all null optional fields`() {
        val r = repo()
        val minimal = WizardSession(chatId = 8L)
        r.save(minimal)
        val loaded = r.get(8L)!!
        assertNull(loaded.transactionType)
        assertNull(loaded.sourceAccount)
        assertNull(loaded.destinationAccount)
        assertNull(loaded.amount)
        assertNull(loaded.sourceAmount)
        assertNull(loaded.destAmount)
        assertNull(loaded.tag)
        assertNull(loaded.category)
        assertNull(loaded.expenseAccountQuery)
        assertNull(loaded.revenueAccountQuery)
        assertNull(loaded.wizardMessageId)
        assertEquals(0, loaded.accountPage)
    }

    // ── WizardStep round-trip for every subtype ───────────────────────────────

    companion object {
        @JvmStatic
        fun allWizardSteps() = listOf(
            WizardStep.SelectType,
            WizardStep.SelectSourceAccount,
            WizardStep.SelectDestinationAccount,
            WizardStep.EnterAmount,
            WizardStep.EnterSourceAmount,
            WizardStep.EnterDestAmount,
            WizardStep.EnterExpenseAccountQuery,
            WizardStep.EnterNewExpenseAccountName,
            WizardStep.EnterRevenueAccountQuery,
            WizardStep.EnterNewRevenueAccountName,
            WizardStep.SelectCategory,
            WizardStep.Preview,
            WizardStep.EnterDateTime,
            WizardStep.SelectTag,
        )
    }

    @ParameterizedTest
    @MethodSource("allWizardSteps")
    fun `each WizardStep survives file round-trip`(step: WizardStep) {
        val r = repo()
        r.save(WizardSession(chatId = 1L, step = step))
        assertEquals(step, r.get(1L)!!.step)
    }

    // ── Corrupt file handling ─────────────────────────────────────────────────

    @Test
    fun `corrupt session file is ignored and treated as empty`() {
        val file = tempDir.resolve("sessions.json").toFile()
        file.writeText("{ this is not valid json }")
        val r = JsonFileWizardSessionRepository(file.absolutePath)
        assertNull(r.get(1L))
    }

    @Test
    fun `save after corrupt file replaces content with valid json`() {
        val file = tempDir.resolve("sessions.json").toFile()
        file.writeText("garbage")
        val r = JsonFileWizardSessionRepository(file.absolutePath)
        r.save(session(1L))
        // A fresh instance should be able to read it back
        val r2 = JsonFileWizardSessionRepository(file.absolutePath)
        assertNotNull(r2.get(1L))
    }

    @Test
    fun `get returns null for record with unknown WizardStep name instead of throwing`() {
        val file = tempDir.resolve("sessions.json").toFile()
        file.writeText("""{"1":{"chatId":1,"step":"UnknownStep","dateTime":"2024-01-01T00:00:00"}}""")
        val r = JsonFileWizardSessionRepository(file.absolutePath)
        assertNull(r.get(1L))
    }

    @Test
    fun `get returns null for record with malformed dateTime instead of throwing`() {
        val file = tempDir.resolve("sessions.json").toFile()
        file.writeText("""{"1":{"chatId":1,"step":"SelectType","dateTime":"not-a-date"}}""")
        val r = JsonFileWizardSessionRepository(file.absolutePath)
        assertNull(r.get(1L))
    }

    // ── Thread safety ─────────────────────────────────────────────────────────

    @Test
    fun `concurrent saves from multiple threads do not lose sessions`() {
        val r = repo()
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val pool = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) { i ->
            pool.submit {
                latch.countDown()
                latch.await() // all threads start simultaneously
                r.save(WizardSession(chatId = i.toLong()))
            }
        }
        pool.shutdown()
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)

        repeat(threadCount) { i ->
            assertNotNull(r.get(i.toLong()), "Session for chatId=$i was lost")
        }
    }
}
