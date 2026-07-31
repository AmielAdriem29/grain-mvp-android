package com.grainmvp.android.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * The two endpoints Android calls, in order, once per rice sample.
 * Nothing else — Android never talks to the database directly, and
 * never calls the dashboard's endpoints (GET /api/replicates, etc.).
 */
interface ApiService {

    @Multipart
    @POST("api/predict")
    suspend fun predict(
        @Part image: MultipartBody.Part,
        @Part("technicianName") technicianName: RequestBody,
        @Part("sampleId") sampleId: RequestBody
    ): PredictResponse

    @Multipart
    @POST("api/replicate")
    suspend fun submitReplicate(
        @Part image: MultipartBody.Part,
        @Part("technicianName") technicianName: RequestBody,
        @Part("sampleId") sampleId: RequestBody,
        @Part("aiPredictedGrains") aiPredictedGrains: RequestBody,
        @Part("confirmedGrains") confirmedGrains: RequestBody,
        @Part("weight") weight: RequestBody
    ): ReplicateResponse
}