package com.grainmvp.android.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds X-API-Key to every request automatically, per the spec:
 * "Build an OkHttp interceptor that adds the X-API-Key header to every
 * outgoing request." No route needs to remember this individually.
 */
class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestWithKey = originalRequest.newBuilder()
            .header("X-API-Key", apiKey)
            .build()
        return chain.proceed(requestWithKey)
    }
}