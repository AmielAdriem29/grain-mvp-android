package com.grainmvp.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grainmvp.android.ui.theme.AccentGreen
import com.grainmvp.android.ui.theme.DividerColor
import com.grainmvp.android.ui.theme.TextPrimaryGranular

/**
 * Primary action button for the GRANULAR field redesign: square corners,
 * filled accent-green, with the corner-tick "blueprint" styling every
 * primary button in the mockups carries. One reusable composable instead
 * of repeating this Button/Modifier chain on every screen.
 *
 * [loading] shows a small spinner ahead of [text] and disables the
 * button, *inside* its fixed [height] -- a separate loading indicator
 * placed below the button changes the Column's total content height
 * while loading, which visibly shifts every sibling below a weighted
 * Spacer each time loading starts/stops (see Submit screen's history).
 * Keeping the spinner inside the button's own unchanging bounds avoids
 * that reflow entirely.
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 56.dp
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentGreen,
            contentColor = Color.White,
            disabledContainerColor = AccentGreen.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        ),
        modifier = modifier
            .height(height)
            .blueprintCorners(Color.White.copy(alpha = 0.85f))
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp).padding(end = 8.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.5.sp)
    }
}

/**
 * Secondary action button for the GRANULAR field redesign: square
 * corners, outlined, no corner-tick styling (the mockups only put the
 * blueprint corners on primary buttons).
 */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RectangleShape,
        border = BorderStroke(1.dp, DividerColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = TextPrimaryGranular,
            disabledContentColor = TextPrimaryGranular.copy(alpha = 0.4f)
        ),
        modifier = modifier.height(height)
    ) {
        Text(text, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    }
}
