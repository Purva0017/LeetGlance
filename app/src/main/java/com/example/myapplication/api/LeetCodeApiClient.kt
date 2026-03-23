package com.example.myapplication.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// ─── Retrofit interface ───────────────────────────────────────────────────────

interface LeetCodeApiService {

    @Headers("Content-Type: application/json")
    @POST("graphql")
    suspend fun getUserStats(
        @Body body: GraphQLRequest
    ): GraphQLResponse
}

// ─── Client ───────────────────────────────────────────────────────────────────

object LeetCodeApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // Username-specific Referer — matches your Spring Boot backend exactly
    // Call buildService(username) each time you make a request
    fun buildService(username: String): LeetCodeApiService {

        val okHttp = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    // LeetCode validates this header — must be user-specific
                    .header("Referer", "https://leetcode.com/$username/")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                // Change to NONE before releasing
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        return Retrofit.Builder()
            .baseUrl("https://leetcode.com/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LeetCodeApiService::class.java)
    }
}