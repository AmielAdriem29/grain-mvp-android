package com.grainmvp.android.network

import com.grainmvp.android.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.flow.first

object SupabaseAuth {

    private val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUB_KEY
        ) {
            install(Auth)
        }
    }

    fun accessTokenOrNull(): String? = client.auth.currentAccessTokenOrNull()

    suspend fun ensureSignedIn() {
        val status = client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        if (status !is SessionStatus.Authenticated) {
            client.auth.signInAnonymously()
        }
    }
}
