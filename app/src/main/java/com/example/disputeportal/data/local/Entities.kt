package com.example.disputeportal.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val cardLastFour: String,
    val merchantName: String,
    val amountMinor: Long,
    val currency: String,
    val occurredAt: String,
)

@Entity(tableName = "disputes")
data class DisputeEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val reason: String,
    val description: String,
    val disputedAmountMinor: Long,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)
