package com.yourname.expensetrackerapp

import java.util.UUID

/**
 * [type] is one of "Income", "Expense" or "Debt". For "Debt", [category] holds the direction
 * ("Payable" or "Receivable") instead of a spending category, and the three debt-only fields
 * below are populated; they stay null/false for Income and Expense so old locally-cached and
 * synced records keep deserializing correctly with no migration needed.
 */
data class Transaction(
    val description: String,
    val amount: Double,
    val type: String,
    val category: String? = null,
    val date: String,
    /** Stable identity used as the Firestore document ID — lets sync/delete target an exact
     *  record instead of matching by field equality. */
    val id: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Debt only: who the money is owed to (Payable) or owed by (Receivable). */
    val personName: String? = null,
    /** Debt only: dd/MM/yyyy, when the debt is expected to be settled. */
    val dueDate: String? = null,
    /** Debt only: true once paid/collected — settled debts drop out of outstanding totals. */
    val isSettled: Boolean = false
)
