package com.grainmvp.android.network

import com.grainmvp.android.BuildConfig
import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Single shared Retrofit instance. BuildConfig.BACKEND_BASE_URL comes
 * from local.properties (see app/build.gradle.kts), never hardcoded here.
 */
object RetrofitClient {

    /** Timeout applied to connect, read and write. */
    private const val HTTP_TIMEOUT_SECONDS = 50L

    private val json = Json { ignoreUnknownKeys = true }

    // Retrofit requires the base URL to end with a trailing slash.
    // BuildConfig's value (from local.properties) may or may not have
    // one, so normalize it here rather than requiring every developer
    // to remember to add it themselves.
    private val normalizedBaseUrl: String
        get() = BuildConfig.BACKEND_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { SupabaseAuth.accessTokenOrNull() })
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    redactHeader("Authorization")
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                },
            )
            .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    val apiService: ApiService by lazy {
        // Log the effective base URL at startup to help debug which URL the app is using
        Log.i("RetrofitClient", "Using backend base URL: $normalizedBaseUrl")
        Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}