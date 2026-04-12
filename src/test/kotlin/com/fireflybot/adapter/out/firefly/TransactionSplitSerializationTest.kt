package com.fireflybot.adapter.out.firefly

import com.fireflybot.adapter.out.firefly.dto.TransactionSplit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransactionSplitSerializationTest {

    private val json = Json { encodeDefaults = false }

    private fun withdrawal(category: String?) = TransactionSplit(
        type = "withdrawal",
        date = "2024-01-15T10:30:00+00:00",
        amount = "50.00",
        description = "Grocery shopping",
        sourceId = "1",
        destinationName = "Grocery Store",
        category = category,
    )

    private fun deposit(category: String?) = TransactionSplit(
        type = "deposit",
        date = "2024-01-15T10:30:00+00:00",
        amount = "2000.00",
        description = "Salary",
        sourceName = "Employer",
        destinationId = "2",
        category = category,
    )

    // ── category_name key ─────────────────────────────────────────────────────

    @Test
    fun `withdrawal category is serialized as category_name`() {
        val serialized = json.encodeToString(withdrawal("Food & Dining"))
        assertTrue("\"category_name\"" in serialized, "expected key 'category_name': $serialized")
        assertFalse("\"category\":" in serialized, "unexpected bare 'category' key: $serialized")
    }

    @Test
    fun `deposit category is serialized as category_name`() {
        val serialized = json.encodeToString(deposit("Income"))
        assertTrue("\"category_name\"" in serialized, "expected key 'category_name': $serialized")
        assertFalse("\"category\":" in serialized, "unexpected bare 'category' key: $serialized")
    }

    // ── null category is omitted ──────────────────────────────────────────────

    @Test
    fun `null category is omitted from withdrawal JSON`() {
        val serialized = json.encodeToString(withdrawal(null))
        assertFalse("category" in serialized, "null category should be absent: $serialized")
    }

    @Test
    fun `null category is omitted from deposit JSON`() {
        val serialized = json.encodeToString(deposit(null))
        assertFalse("category" in serialized, "null category should be absent: $serialized")
    }

    // ── other snake_case fields ───────────────────────────────────────────────

    @Test
    fun `source_id and destination_name are correctly serialized`() {
        val serialized = json.encodeToString(withdrawal("Food"))
        assertTrue("\"source_id\"" in serialized)
        assertTrue("\"destination_name\"" in serialized)
    }
}
