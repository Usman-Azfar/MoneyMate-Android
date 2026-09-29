package com.yourname.expensetrackerapp

data class NotificationEntry(
    val id: String,
    val period: BudgetPeriod,
    val title: String,
    val message: String,
    val timestampMillis: Long
)
