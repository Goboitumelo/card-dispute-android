package com.example.disputeportal.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface DisputeApi {
    @GET("api/transactions")
    suspend fun transactions(): List<TransactionDto>

    @GET("api/disputes")
    suspend fun disputes(): List<DisputeDto>

    @POST("api/disputes")
    suspend fun submit(@Body request: SubmitDisputeRequestDto): DisputeDto

    @POST("api/disputes/{id}/withdraw")
    suspend fun withdraw(@Path("id") id: String, @Body request: WithdrawRequestDto): DisputeDto
}
