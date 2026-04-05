package com.fireflybot.adapter.out.mock

import com.fireflybot.application.wizard.WizardSession
import com.fireflybot.application.wizard.WizardStep
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InMemoryWizardSessionRepositoryTest {

    private val repo = InMemoryWizardSessionRepository()

    private fun session(chatId: Long) = WizardSession(chatId = chatId)

    @Test
    fun `get returns null when no session stored`() {
        assertNull(repo.get(1L))
    }

    @Test
    fun `save and get returns stored session`() {
        val session = session(10L)
        repo.save(session)
        assertEquals(session, repo.get(10L))
    }

    @Test
    fun `save overwrites existing session`() {
        repo.save(session(10L))
        val updated = WizardSession(chatId = 10L, step = WizardStep.EnterAmount)
        repo.save(updated)
        assertEquals(WizardStep.EnterAmount, repo.get(10L)!!.step)
    }

    @Test
    fun `delete removes stored session`() {
        repo.save(session(10L))
        repo.delete(10L)
        assertNull(repo.get(10L))
    }

    @Test
    fun `delete is no-op when session does not exist`() {
        repo.delete(99L) // should not throw
        assertNull(repo.get(99L))
    }

    @Test
    fun `sessions for different chats are isolated`() {
        val s1 = session(1L)
        val s2 = WizardSession(chatId = 2L, step = WizardStep.EnterAmount)
        repo.save(s1)
        repo.save(s2)

        assertEquals(WizardStep.SelectType, repo.get(1L)!!.step)
        assertEquals(WizardStep.EnterAmount, repo.get(2L)!!.step)
    }

    @Test
    fun `deleting one chat session does not affect another`() {
        repo.save(session(1L))
        repo.save(session(2L))
        repo.delete(1L)

        assertNull(repo.get(1L))
        assertNotNull(repo.get(2L))
    }
}
