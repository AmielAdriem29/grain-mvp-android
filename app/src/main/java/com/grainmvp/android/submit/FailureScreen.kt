package com.grainmvp.android.submit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Screen 07 (Failure & retry) of the GRANULAR field redesign. Replaces
 * the old raw `"${exception.javaClass.simpleName}: ${exception.message}"`
 * string with a plain-language explanation, while still keeping the raw
 * exception class name + endpoint visible in small print for anyone who
 * needs it for support.
 *
 * Only `SocketTimeoutException` gets a specific message per the design
 * brief; everything else falls back to a generic sentence -- see
 * [friendlyErrorMessage].
 *
 * Honesty note (see docs/IMPLEMENTATION.md, dated section): "Keep on
 * device" cannot literally hold this sample for a later automatic
 * retry -- this app has no persistent local queue. [onKeepOnDevice] is
 * wired by the caller to just end the attempt and return to Welcome,
 * the same as today's lack of a retry queue. Flagged as a real UX gap
 * worth a follow-up conversation with whoever specced "send it later."
 */
@Composable
fun FailureScreen(
    exceptionClassName: String,
    sampleId: String,
    confirmedGrainCount: Int,
    onRetry: () -> Unit,
    onKeepOnDevice: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .padding(20.dp)
    ) {
        Text(
            "Submit replicate",
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
                "NOT SENT",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = FailureEyebrow
            )
            Text(
                friendlyErrorMessage(exceptionClassName),
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 16.sp,
                color = Neutral900
            )
            Text(
                "$sampleId is held on this device. Retry now, or keep scanning and send it later.",
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 13.sp,
                color = Neutral700
            )
            Text(
                "$exceptionClassName · /api/replicate",
                modifier = Modifier.padding(top = 6.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Neutral600
            )
        }

        BlueprintFrame(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .alpha(0.75f)
        ) {
            SummaryRow("Sample ID", sampleId)
            RowDivider()
            SummaryRow("Confirmed grains", confirmedGrainCount.toString())
        }

        Spacer(modifier = Modifier.weight(1f))

        PrimaryActionButton(text = "Retry now", onClick = onRetry, modifier = Modifier.fillMaxWidth())
        SecondaryActionButton(
            text = "Keep on device",
            onClick = onKeepOnDevice,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
    }
}

/**
 * Maps an exception's simple class name to a plain-language sentence.
 * `SocketTimeoutException` gets the specific wording the design calls
 * for; everything else gets a sensible generic fallback.
 */
private fun friendlyErrorMessage(exceptionClassName: String): String = when (exceptionClassName) {
    "SocketTimeoutException" -> "The server didn't answer in 30 seconds."
    else -> "Something went wrong sending this sample."
}
