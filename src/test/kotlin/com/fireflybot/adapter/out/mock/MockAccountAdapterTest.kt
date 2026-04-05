package com.fireflybot.adapter.out.mock

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MockAccountAdapterTest {

    private val adapter = MockAccountAdapter()

    // ── getAssetAccounts ──────────────────────────────────────────────────────

    @Test
    fun `getAssetAccounts returns all 7 asset accounts`() {
        val accounts = adapter.getAssetAccounts()
        assertEquals(7, accounts.size)
    }

    @Test
    fun `getAssetAccounts returns accounts with non-blank ids and names`() {
        adapter.getAssetAccounts().forEach { account ->
            assertTrue(account.id.isNotBlank())
            assertTrue(account.name.isNotBlank())
            assertTrue(account.currencyCode.isNotBlank())
        }
    }

    // ── searchExpenseAccounts ─────────────────────────────────────────────────

    @Test
    fun `searchExpenseAccounts returns all accounts for empty query`() {
        val results = adapter.searchExpenseAccounts("")
        assertEquals(10, results.size)
    }

    @Test
    fun `searchExpenseAccounts filters case-insensitively`() {
        val lower = adapter.searchExpenseAccounts("grocery")
        val upper = adapter.searchExpenseAccounts("GROCERY")
        val mixed = adapter.searchExpenseAccounts("Grocery")
        assertEquals(lower, upper)
        assertEquals(lower, mixed)
        assertTrue(lower.isNotEmpty())
        assertTrue(lower.all { it.name.contains("Grocery", ignoreCase = true) })
    }

    @Test
    fun `searchExpenseAccounts returns empty list when no match`() {
        val results = adapter.searchExpenseAccounts("zzz_no_match_zzz")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchExpenseAccounts returns subset matching query`() {
        val results = adapter.searchExpenseAccounts("Netflix")
        assertEquals(1, results.size)
        assertEquals("Netflix", results[0].name)
    }

    // ── searchRevenueAccounts ─────────────────────────────────────────────────

    @Test
    fun `searchRevenueAccounts returns all accounts for empty query`() {
        val results = adapter.searchRevenueAccounts("")
        assertEquals(8, results.size)
    }

    @Test
    fun `searchRevenueAccounts filters case-insensitively`() {
        val lower = adapter.searchRevenueAccounts("paycheck")
        val upper = adapter.searchRevenueAccounts("PAYCHECK")
        assertEquals(lower, upper)
        assertTrue(lower.isNotEmpty())
    }

    @Test
    fun `searchRevenueAccounts returns empty list when no match`() {
        val results = adapter.searchRevenueAccounts("zzz_no_match_zzz")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchRevenueAccounts returns subset matching query`() {
        val results = adapter.searchRevenueAccounts("Pension")
        assertEquals(1, results.size)
        assertEquals("Pension", results[0].name)
    }

    // ── createExpenseAccount ──────────────────────────────────────────────────

    @Test
    fun `createExpenseAccount returns account with given name and currency`() {
        val account = adapter.createExpenseAccount("New Shop", "EUR")
        assertEquals("New Shop", account.name)
        assertEquals("EUR", account.currencyCode)
        assertTrue(account.id.isNotBlank())
    }

    @Test
    fun `createExpenseAccount id starts with new_ prefix`() {
        val account = adapter.createExpenseAccount("Shop A", "USD")
        assertTrue(account.id.startsWith("new_"))
    }

    // ── createRevenueAccount ──────────────────────────────────────────────────

    @Test
    fun `createRevenueAccount returns account with given name and currency`() {
        val account = adapter.createRevenueAccount("Freelance Client", "USD")
        assertEquals("Freelance Client", account.name)
        assertEquals("USD", account.currencyCode)
        assertTrue(account.id.isNotBlank())
    }

    @Test
    fun `createRevenueAccount id starts with new_ prefix`() {
        val account = adapter.createRevenueAccount("Client A", "USD")
        assertTrue(account.id.startsWith("new_"))
    }
}
