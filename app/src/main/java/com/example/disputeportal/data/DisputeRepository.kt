package com.example.disputeportal.data

import androidx.room.withTransaction
import com.example.disputeportal.data.local.AppDatabase
import com.example.disputeportal.data.local.DisputeEntity
import com.example.disputeportal.data.local.TransactionEntity
import com.example.disputeportal.data.remote.DisputeApi
import com.example.disputeportal.data.remote.DisputeDto
import com.example.disputeportal.data.remote.HistoryDto
import com.example.disputeportal.data.remote.LoginRequestDto
import com.example.disputeportal.data.remote.SubmitDisputeRequestDto
import com.example.disputeportal.data.remote.TransactionDto
import com.example.disputeportal.data.remote.WithdrawRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DisputeRepository(
    private val api: DisputeApi,
    private val db: AppDatabase,
) {
    /** Emits cached transactions, newest first, and re-emits when they change. */
    fun observeTransactions(): Flow<List<TransactionEntity>> = db.transactionDao().observeAll()

    /** Emits cached disputes, newest first, and re-emits when they change. */
    fun observeDisputes(): Flow<List<DisputeEntity>> = db.disputeDao().observeAll()

    /**
     * Replaces the cached transactions and disputes with fresh data from the API.
     *
     * The API is fetched before the cache is touched, so a failed request keeps the last good data.
     */
    suspend fun refresh() {
        val transactions = api.transactions().map { it.toEntity() }
        val disputes = api.disputes().map { it.toEntity() }
        db.withTransaction {
            db.transactionDao().deleteAll()
            db.transactionDao().insertAll(transactions)
            db.disputeDao().deleteAll()
            db.disputeDao().insertAll(disputes)
        }
    }

    /** Deletes all cached data. */
    suspend fun clearCache() = withContext(Dispatchers.IO) { db.clearAllTables() }

    /**
     * Exchanges the credentials for a bearer token.
     *
     * @param email the user's email address
     * @param password the user's password
     * @return the raw bearer token
     */
    suspend fun login(email: String, password: String): String =
        api.login(LoginRequestDto(email, password)).token

    /** Revokes the current bearer token on the server. */
    suspend fun logout() {
        api.logout()
    }

    /**
     * Opens a dispute.
     *
     * @param transactionId the transaction being disputed
     * @param reason one of the backend `DisputeReason` names
     * @param description the customer's explanation
     * @param disputedAmountMinor the disputed amount in minor units
     */
    suspend fun submitDispute(
        transactionId: String,
        reason: String,
        description: String,
        disputedAmountMinor: Long,
    ) {
        api.submit(SubmitDisputeRequestDto(transactionId, reason, description, disputedAmountMinor))
    }

    /**
     * Withdraws a dispute.
     *
     * @param disputeId the dispute to withdraw
     */
    suspend fun withdrawDispute(disputeId: String) {
        api.withdraw(disputeId, WithdrawRequestDto(note = null))
    }

    /**
     * Fetches the status history of a dispute. The result is not cached.
     *
     * @param disputeId the dispute to look up
     * @return the status changes recorded for the dispute
     */
    suspend fun history(disputeId: String): List<HistoryDto> = api.history(disputeId)
}

/** Maps a transaction DTO to its cached entity. */
private fun TransactionDto.toEntity() = TransactionEntity(
    id = id,
    cardLastFour = cardLastFour,
    merchantName = merchantName,
    amountMinor = amountMinor,
    currency = currency,
    occurredAt = occurredAt,
)

/** Maps a dispute DTO to its cached entity. */
private fun DisputeDto.toEntity() = DisputeEntity(
    id = id,
    transactionId = transactionId,
    reason = reason,
    description = description,
    disputedAmountMinor = disputedAmountMinor,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
