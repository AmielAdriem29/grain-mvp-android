package com.grainmvp.android.testsupport

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.grainmvp.android.network.ApiService
import com.grainmvp.android.network.AuthInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import retrofit2.Retrofit
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/**
 * The local test backend started by testing/run-android-tests.ps1 (or .sh).
 *
 * The runner passes three instrumentation arguments: baseUrl, captureToken
 * and dashboardToken. Without them every test here is skipped, not failed,
 * so a plain `gradlew connectedAndroidTest` never reaches a real server.
 *
 * [app] is the app's own [ApiService] and [AuthInterceptor] with the same
 * JSON settings and timeouts as RetrofitClient. The one difference is where
 * the URL and token come from: RetrofitClient reads them from BuildConfig and
 * the live Supabase session, which would point at production.
 */
object TestBackend {

    /** Same as RetrofitClient.HTTP_TIMEOUT_SECONDS. */
    private const val HTTP_TIMEOUT_SECONDS = 50L

    /** Same settings as RetrofitClient's Json. */
    private val json = Json { ignoreUnknownKeys = true }

    private val args get() = InstrumentationRegistry.getArguments()

    private val baseUrl: String? get() = args.getString("baseUrl")?.let { if (it.endsWith("/")) it else "$it/" }
    private val captureToken: String? get() = args.getString("captureToken")
    private val dashboardToken: String? get() = args.getString("dashboardToken")

    /** Skips the calling test when the runner didn't start a local backend. */
    fun assumeConfigured() {
        assumeTrue(
            "No local test backend. Run testing/run-android-tests.ps1 (see testing/README.md).",
            baseUrl != null && captureToken != null && dashboardToken != null
        )
        val url = baseUrl!!
        check(url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost") || url.startsWith("http://10.0.2.2")) {
            "Refusing to run against $url: these tests only run against a local backend."
        }
    }

    /** The app's API, signed in as the app would be (anonymous user). */
    val app: ApiService by lazy { retrofit(captureToken!!).create(ApiService::class.java) }

    /** Reads records back the way the dashboard does, to see what the database stored. */
    val dashboard: DashboardReadApi by lazy { retrofit(dashboardToken!!).create(DashboardReadApi::class.java) }

    /** The stored record for [id], as raw JSON so nothing is lost to parsing. */
    suspend fun storedReplicate(id: String): JsonObject =
        dashboard.replicates()
            .map { it.jsonObject }
            .firstOrNull { it["id"]?.jsonPrimitive?.content == id }
            ?: error("Record $id was not returned by GET /api/replicates")

    private fun retrofit(token: String): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { token })
            .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl!!)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}

/** Read-only dashboard endpoint, used only to check what was stored. */
interface DashboardReadApi {
    @GET("api/replicates")
    suspend fun replicates(): JsonArray
}

/** Evidence lines. The runner saves this logcat tag to testing/build/results/. */
fun evidence(tcId: String, message: String) {
    Log.i("GrainTest", "$tcId | $message")
}
