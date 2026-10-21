package com.example.disputeportal.data

import androidx.room.withTransaction
import com.example.disputeportal.data.remote.SubmitDisputeRequestDto
import com.example.disputeportal.data.remote.WithdrawRequestDto
import java.io.IOException
import retrofit2.HttpException
import com.example.disputeportal.data.local.AppDatabase
import com.example.disputeportal.data.local.DisputeEntity
import com.example.disputeportal.data.local.TransactionEntity
import com.example.disputeportal.data.remote.DisputeApi
import com.example.disputeportal.data.remote.DisputeDto
import com.example.disputeportal.data.remote.HistoryDto
import com.example.disputeportal.data.remote.TransactionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DisputeRepository(
    private val api: DisputeApi,
    private val db: AppDatabase,
) {
    fun observeTransactions(): Flow<List<TransactionEntity>> = db.transactionDao().observeAll()

    fun observeDisputes(): Flow<List<DisputeEntity>> = db.disputeDao().observeAll()

    // Fetch before touching the cache so a failed request keeps the last good data.
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

    suspend fun clearCache() = withContext(Dispatchers.IO) { db.clearAllTables() }

    suspend fun submitDispute(
        transactionId: String,
        reason: String,
        description: String,
        disputedAmountMinor: Long,
    ) {
        api.submit(SubmitDisputeRequestDto(transactionId, reason, description, disputedAmountMinor))
        refreshAfterWrite()
    }

    suspend fun withdrawDispute(disputeId: String) {
        api.withdraw(disputeId, WithdrawRequestDto(note = null))
        refreshAfterWrite()
    }

    // Not cached: history is small and only needed when a dispute is opened.
    suspend fun history(disputeId: String): List<HistoryDto> = api.history(disputeId)

    // The write succeeded, so a failed cache refresh is ignored; Refresh retries it.
    private suspend fun refreshAfterWrite() {
        try {
            refresh()
        } catch (e: IOException) {
        } catch (e: HttpException) {
        }
    }
}

private fun TransactionDto.toEntity() = TransactionEntity(
    id = id,
    cardLastFour = cardLastFour,
    merchantName = merchantName,
    amountMinor = amountMinor,
    currency = currency,
    occurredAt = occurredAt,
)

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
