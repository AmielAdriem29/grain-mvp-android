package com.grainmvp.android.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grainmvp.android.R
import com.grainmvp.android.ui.components.PrimaryActionButton
import com.grainmvp.android.ui.theme.AccentGreen
import com.grainmvp.android.ui.theme.Neutral600
import com.grainmvp.android.ui.theme.Neutral700
import com.grainmvp.android.ui.theme.SurfaceBg

/**
 * Screen 01 (Opening) of the GRANULAR field redesign -- the launch
 * screen: wordmark, what the app does, one way forward. Camera
 * permission is still only requested after "Start a session" is tapped,
 * and that now leads to the new Start Session screen (technician/sample
 * ID), not straight into the camera -- see home/StartSessionScreen.kt.
 *
 * Renamed from the old HomeScreen()/"GrainMVP" wording to match the
 * redesign's "GRANULAR" branding (see docs/IMPLEMENTATION.md).
 */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.granular_logo),
                contentDescription = "GRANULAR",
                modifier = Modifier.size(104.dp)
            )

            Text(
                "GRANULAR",
                modifier = Modifier.padding(top = 18.dp),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                letterSpacing = 2.sp,
                color = AccentGreen
            )

            Text(
                "RICE GRAIN QUALITY ASSESSMENT",
                modifier = Modifier.padding(top = 10.dp),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = 2.sp,
                color = Neutral700,
                textAlign = TextAlign.Center
            )

            Text(
                "Photograph a rice sample, review the detection, and submit the assessment.",
                modifier = Modifier.padding(top = 14.dp, start = 12.dp, end = 12.dp),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = Neutral700,
                textAlign = TextAlign.Center
            )
        }

        PrimaryActionButton(
            text = "Start a session",
            onClick = onStart,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "Camera access is requested after this step.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            fontSize = 12.sp,
            color = Neutral600,
            textAlign = TextAlign.Center
        )
    }
}
