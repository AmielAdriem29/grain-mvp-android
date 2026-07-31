package com.grainmvp.android.network

import com.grainmvp.android.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Single shared Retrofit instance. BuildConfig.BACKEND_BASE_URL and
 * BACKEND_API_KEY come from local.properties (see app/build.gradle.kts) —
 * never hardcoded here.
 */
object RetrofitClient {

    private val json = Json { ignoreUnknownKeys = true }

    // Retrofit requires the base URL to end with a trailing slash.
    // BuildConfig's value (from local.properties) may or may not have
    // one, so normalize it here rather than requiring every developer
    // to remember to add it themselves.
    private val normalizedBaseUrl: String
        get() = BuildConfig.BACKEND_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(BuildConfig.BACKEND_API_KEY))
            .addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
            )
            // Spec: "Set a 30-second timeout on the HTTP client."
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}