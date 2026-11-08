package com.grainmvp.android.network

import android.graphics.Bitmap
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

/** JPEG quality (0 to 100) for the uploaded sample image. */
private const val UPLOAD_JPEG_QUALITY = 90

/** Compresses a Bitmap to JPEG and wraps it as the "image" multipart part. */
fun Bitmap.toImagePart(partName: String = "image"): MultipartBody.Part {
    val stream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, UPLOAD_JPEG_QUALITY, stream)
    val requestBody = stream.toByteArray().toRequestBody("image/jpeg".toMediaType())
    return MultipartBody.Part.createFormData(partName, "capture.jpg", requestBody)
}

/** Wraps a plain string value as a multipart text part (not JSON). */
fun String.toTextPart(): RequestBody =
    toRequestBody("text/plain".toMediaType())