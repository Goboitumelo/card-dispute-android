package com.example.disputeportal.data.remote

// Field names match the backend JSON. Timestamps stay as ISO-8601 strings.
data class TransactionDto(
    val id: String,
    val cardLastFour: String,
    val merchantName: String,
    val amountMinor: Long,
    val currency: String,
    val occurredAt: String,
)

data class DisputeDto(
    val id: String,
    val transactionId: String,
    val reason: String,
    val description: String,
    val disputedAmountMinor: Long,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)

// Null fields are left out of the JSON, so an empty withdraw body is sent when no note is given.
data class SubmitDisputeRequestDto(
    val transactionId: String,
    val reason: String,
    val description: String,
    val disputedAmountMinor: Long,
)

data class WithdrawRequestDto(val note: String?)
