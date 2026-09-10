package com.grainmvp.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grainmvp.android.ui.theme.DividerColor

/**
 * Draws four short corner tick-marks just inside an element's bounds --
 * the recurring "blueprint" accent in the GRANULAR field redesign (the
 * session form, the submit summary, the failure card, the result plate,
 * every primary button). Implemented as a Modifier rather than requiring
 * [BlueprintFrame] specifically, so [PrimaryActionButton] can reuse the
 * exact same corner-drawing code without a wrapper Composable.
 *
 * Each tick is inset by half the stroke width from the edge.
 * `drawLine` centers its stroke on the path it's given, so a tick drawn
 * exactly at the boundary (x/y == 0 or w/h) bleeds half its stroke width
 * outside the element's own bounds -- nothing here clips a `drawWithContent`
 * overlay back to those bounds. That bleed is what made a primary button
 * with corner ticks look visibly bigger/uneven next to a secondary button
 * without them, even at an identical `Modifier.height`. Insetting keeps
 * the full visual stroke inside [0, w] x [0, h].
 */
fun Modifier.blueprintCorners(
    color: Color,
    length: Dp = 10.dp,
    strokeWidth: Dp = 1.5.dp
): Modifier = this.drawWithContent {
    drawContent()
    val len = length.toPx()
    val sw = strokeWidth.toPx()
    val inset = sw / 2f
    val w = size.width
    val h = size.height

    // top-left
    drawLine(color, Offset(inset, inset), Offset(len, inset), sw)
    drawLine(color, Offset(inset, inset), Offset(inset, len), sw)
    // top-right
    drawLine(color, Offset(w - inset, inset), Offset(w - len, inset), sw)
    drawLine(color, Offset(w - inset, inset), Offset(w - inset, len), sw)
    // bottom-left
    drawLine(color, Offset(inset, h - inset), Offset(len, h - inset), sw)
    drawLine(color, Offset(inset, h - inset), Offset(inset, h - len), sw)
    // bottom-right
    drawLine(color, Offset(w - inset, h - inset), Offset(w - len, h - inset), sw)
    drawLine(color, Offset(w - inset, h - inset), Offset(w - inset, h - len), sw)
}

/**
 * The bordered "blueprint" card used repeatedly across the GRANULAR
 * field redesign: a 1px divider-colored border (or a custom
 * border/background color, e.g. the failure card's red border or the
 * result plate's green fill) plus four corner tick-marks.
 *
 * One reusable composable instead of copy-pasting the border + corner
 * drawing code on every screen that needs the look (session form on
 * Start Session, summary on Submit, error card on Failure, grade plate
 * on Result).
 */
@Composable
fun BlueprintFrame(
    modifier: Modifier = Modifier,
    borderColor: Color = DividerColor,
    backgroundColor: Color = Color.Transparent,
    tickColor: Color = borderColor,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(backgroundColor, RectangleShape)
            .border(1.dp, borderColor, RectangleShape)
            .blueprintCorners(tickColor)
            .padding(contentPadding),
        content = content
    )
}
