package com.grainmvp.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Consistent branded header used at the top of every screen: solid
 * primary-color bar with the app name and the current screen's label.
 * Gives every screen a shared visual identity instead of floating
 * plain black text on a white background, and doubles as a simple
 * "where am I" indicator since this app has no real navigation bar.
 */
@Composable
fun AppHeader(screenLabel: String) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                "GrainMVP",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Start
            )
            Text(
                screenLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
            )
        }
    }
}