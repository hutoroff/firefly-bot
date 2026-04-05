package com.fireflybot.telegram.wizard

sealed class WizardStep {
    data object SelectType : WizardStep()
    data object SelectSourceAccount : WizardStep()

    // ── Transfer steps ────────────────────────────────────────────────────────
    data object SelectDestinationAccount : WizardStep()
    /** Amount input when source and destination share the same currency. */
    data object EnterAmount : WizardStep()
    /** First amount input for cross-currency transfers (withdrawal from source). */
    data object EnterSourceAmount : WizardStep()
    /** Second amount input for cross-currency transfers (deposit to destination). */
    data object EnterDestAmount : WizardStep()

    // ── Withdrawal steps ──────────────────────────────────────────────────────
    /** User types part of an expense account name to search. */
    data object EnterExpenseAccountQuery : WizardStep()
    /** User types a name for a brand-new expense account. */
    data object EnterNewExpenseAccountName : WizardStep()

    // ── Deposit steps ─────────────────────────────────────────────────────────
    /** User types part of a revenue account name to search. */
    data object EnterRevenueAccountQuery : WizardStep()
    /** User types a name for a brand-new revenue account. */
    data object EnterNewRevenueAccountName : WizardStep()

    // ── Withdrawal + Deposit steps ────────────────────────────────────────────
    /** User selects a transaction category from a paginated list. */
    data object SelectCategory : WizardStep()

    // ── Common steps ──────────────────────────────────────────────────────────
    data object Preview : WizardStep()
    data object EnterDateTime : WizardStep()
    data object SelectTag : WizardStep()
}
