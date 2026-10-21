package com.example.disputeportal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    /** Emits all cached transactions, newest first, whenever they change. */
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /**
     * Inserts the given transactions, replacing any with the same ID.
     *
     * @param items the transactions to store
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TransactionEntity>)

    /** Removes all cached transactions. */
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface DisputeDao {
    /** Emits all cached disputes, newest first, whenever they change. */
    @Query("SELECT * FROM disputes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DisputeEntity>>

    /**
     * Inserts the given disputes, replacing any with the same ID.
     *
     * @param items the disputes to store
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DisputeEntity>)

    /** Removes all cached disputes. */
    @Query("DELETE FROM disputes")
    suspend fun deleteAll()
}
