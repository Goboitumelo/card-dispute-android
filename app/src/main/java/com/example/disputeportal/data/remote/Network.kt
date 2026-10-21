package com.example.disputeportal.data.remote

import com.example.disputeportal.session.Session
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Builds a [DisputeApi] that attaches the current session's bearer token to each request.
 *
 * @param session source of the token, read on every request
 * @param baseUrl base URL of the API, ending in a slash
 * @return a Retrofit implementation of [DisputeApi]
 */
fun buildApi(session: Session, baseUrl: String): DisputeApi {
    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val login = session.login.value
            val authorised = if (login == null) {
                request
            } else {
                request.newBuilder()
                    .header("Authorization", "Bearer ${login.token}")
                    .build()
            }
            chain.proceed(authorised)
        }
        .build()

    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DisputeApi::class.java)
}
