package com.example.disputeportal.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface DisputeApi {
    /** Fetches the signed-in customer's transactions. */
    @GET("api/transactions")
    suspend fun transactions(): List<TransactionDto>

    /** Fetches the signed-in customer's disputes. */
    @GET("api/disputes")
    suspend fun disputes(): List<DisputeDto>

    /**
     * Opens a dispute against a transaction.
     *
     * @param request the dispute details
     * @return the created dispute
     */
    @POST("api/disputes")
    suspend fun submit(@Body request: SubmitDisputeRequestDto): DisputeDto

    /**
     * Withdraws an open dispute.
     *
     * @param id the dispute to withdraw
     * @param request the withdrawal details, with an optional note
     * @return the updated dispute
     */
    @POST("api/disputes/{id}/withdraw")
    suspend fun withdraw(@Path("id") id: String, @Body request: WithdrawRequestDto): DisputeDto

    /**
     * Fetches the status change history of a dispute.
     *
     * @param id the dispute to look up
     * @return the status changes recorded for the dispute
     */
    @GET("api/disputes/{id}/history")
    suspend fun history(@Path("id") id: String): List<HistoryDto>
}
