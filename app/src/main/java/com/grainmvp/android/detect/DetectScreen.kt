package com.grainmvp.android.detect

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.network.RetrofitClient
import com.grainmvp.android.network.toImagePart
import com.grainmvp.android.network.toTextPart
import com.grainmvp.android.session.SessionInfo
import com.grainmvp.android.ui.components.BlueprintFrame
import com.grainmvp.android.ui.components.PrimaryActionButton
import com.grainmvp.android.ui.components.SecondaryActionButton
import com.grainmvp.android.ui.theme.FailureBg
import com.grainmvp.android.ui.theme.FailureBorder
import com.grainmvp.android.ui.theme.FailureEyebrow
import com.grainmvp.android.ui.theme.Neutral600
import com.grainmvp.android.ui.theme.Neutral700
import com.grainmvp.android.ui.theme.Neutral900
import com.grainmvp.android.ui.theme.SurfaceBg
import com.grainmvp.android.ui.theme.TextPrimaryGranular
import kotlinx.coroutines.CancellationException

suspend fun predictGrains(image: Bitmap, session: SessionInfo): List<GrainBox> =
    RetrofitClient.apiService.predict(
        image = image.toImagePart(),
        technicianName = session.technicianName.toTextPart(),
        sampleId = session.sampleId.toTextPart()
    ).grains

@Composable
fun DetectScreen(
    image: Bitmap,
    session: SessionInfo,
    onDetected: (List<GrainBox>) -> Unit,
    onContinueWithoutProposals: () -> Unit,
    onRetake: () -> Unit
) {
    var attempt by remember { mutableStateOf(0) }
    var errorClassName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(attempt) {
        errorClassName = null
        try {
            onDetected(predictGrains(image, session))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorClassName = e.javaClass.simpleName
        }
    }

    val error = errorClassName
    if (error == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceBg),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    "Detecting grains…",
                    modifier = Modifier.padding(top = 16.dp),
                    fontSize = 16.sp,
                    color = TextPrimaryGranular
                )
                Text(
                    session.sampleId,
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 12.sp,
                    color = Neutral600
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceBg)
                .padding(20.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                "Detect grains",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = TextPrimaryGranular
            )

            BlueprintFrame(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                backgroundColor = FailureBg,
                borderColor = FailureBorder,
                tickColor = FailureBorder,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp)
            ) {
                Text(
                    "DETECTION FAILED",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.6.sp,
                    color = FailureEyebrow
                )
                Text(
                    friendlyDetectError(error),
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 16.sp,
                    color = Neutral900
                )
                Text(
                    "Try again, retake the photo, or continue without proposals and add every box by hand.",
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 13.sp,
                    color = Neutral700
                )
                Text(
                    "$error · /api/predict",
                    modifier = Modifier.padding(top = 6.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Neutral600
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryActionButton(
                text = "Try again",
                onClick = { attempt++ },
                modifier = Modifier.fillMaxWidth()
            )
            SecondaryActionButton(
                text = "Continue without proposals",
                onClick = onContinueWithoutProposals,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
            SecondaryActionButton(
                text = "Retake photo",
                onClick = onRetake,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
        }
    }
}

private fun friendlyDetectError(exceptionClassName: String): String = when (exceptionClassName) {
    "SocketTimeoutException" -> "The server took too long to answer."
    else -> "Something went wrong detecting grains."
}
