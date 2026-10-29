package com.grainmvp.android.correction

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.ui.components.SecondaryActionButton
import com.grainmvp.android.ui.components.PrimaryActionButton
import com.grainmvp.android.ui.components.blueprintCorners
import com.grainmvp.android.ui.theme.Accent700
import com.grainmvp.android.ui.theme.Accent900
import com.grainmvp.android.ui.theme.AccentGreen
import com.grainmvp.android.ui.theme.DetectionBlue
import com.grainmvp.android.ui.theme.DividerColor
import com.grainmvp.android.ui.theme.Neutral500
import com.grainmvp.android.ui.theme.Neutral700
import com.grainmvp.android.ui.theme.SurfaceBg
import com.grainmvp.android.ui.theme.TextPrimaryGranular

private const val IMAGE_SIZE = 1024

/** Zoom range: 1x (fit-to-width, no zoom) up to 4x -- close enough to make
 *  individual grains easy to tap without losing track of where you are
 *  on the tray. */
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f

/** Multiplier applied per tap of the floating +/- zoom buttons. */
private const val ZOOM_STEP = 1.5f

/**
 * Screen 05 (Review & correct) of the GRANULAR field redesign.
 *
 * Replaces the old "the weak screen today" version with:
 *  - a live running count: AI detected vs. you removed/added, computed
 *    reactively as confirmedCount = initialGrains.size - removed + added
 *  - an explicit Remove/Add mode (the segmented control below the
 *    image), so a tap is never ambiguous about what it will do -- see
 *    [GrainCorrectionMode] / [applyGrainTap] in CorrectionLogic.kt
 *  - box states distinguished by line style as well as color: solid
 *    blue = detected/untouched, white dashed = removed, dark green
 *    (with a small white halo, for visibility against a dark photo) =
 *    added. The halo used to be twice as large, making added boxes
 *    look noticeably bigger than the others for no added clarity;
 *    shrunk to keep the footprint close to the same while still
 *    standing out against a dark background
 *  - pinch-to-zoom and pan (single-finger drag once zoomed) on the
 *    image, so a technician can zoom into a dense cluster of grains
 *    before tapping -- see the `awaitEachGesture` block below and
 *    [screenTapToImageCoords] / [clampPan] in CorrectionLogic.kt. The
 *    floating +/- buttons over the image are discrete zoom steps;
 *    pinch is the primary way to zoom. There's no separate floating
 *    mode toggle any more -- the segmented control is the only mode
 *    switch, so it isn't duplicated.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun CorrectionScreen(
    image: Bitmap,
    initialGrains: List<GrainBox>,
    onRetakePhoto: () -> Unit,
    onSubmit: (confirmedGrains: List<GrainBox>, weight: String) -> Unit
) {
    val grains = remember(initialGrains) {
        mutableStateListOf<GrainBox>().apply { addAll(initialGrains) }
    }
    var weightText by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(GrainCorrectionMode.REMOVE) }

    // Zoom/pan state resets with the image (a "New photo" tap starts
    // fresh rather than carrying over the old photo's zoom level).
    var zoom by remember(initialGrains) { mutableStateOf(MIN_ZOOM) }
    var panOffset by remember(initialGrains) { mutableStateOf(Offset.Zero) }

    val aiCount = initialGrains.size
    val removedCount = grains.count { it.action == "removed" }
    val addedCount = grains.count { it.action == "added" }
    val confirmedCount = aiCount - removedCount + addedCount

    fun applyTap(imageX: Float, imageY: Float) {
        val updated = applyGrainTap(grains, imageX, imageY, mode)
        if (updated !== grains) {
            grains.clear()
            grains.addAll(updated)
        }
    }

    /** Applies a discrete zoom step from the +/- buttons, re-clamping pan to the new zoom level. */
    fun stepZoom(factor: Float, boxSizePx: Float) {
        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        zoom = newZoom
        panOffset = Offset(
            clampPan(panOffset.x, boxSizePx, newZoom),
            clampPan(panOffset.y, boxSizePx, newZoom)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    "IMMATURE GRAINS",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.6.sp,
                    color = Accent700
                )
                Text(
                    "$confirmedCount",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 38.sp,
                    color = TextPrimaryGranular
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("AI detected $aiCount", fontSize = 12.sp, color = Neutral700)
                Text("You removed $removedCount · added $addedCount", fontSize = 12.sp, color = Neutral700)
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.5.dp, color = DividerColor)
                // Corner ticks matching the "blueprint" frame used
                // elsewhere (session form, submit summary, result plate),
                // so the image reads as a clearly contained element
                // rather than blending into the page background.
                .blueprintCorners(Accent700)
        ) {
            val displayScale = constraints.maxWidth.toFloat() / IMAGE_SIZE
            val boxSizePx = constraints.maxWidth.toFloat()

            Box(
                modifier = Modifier
                    .width(maxWidth)
                    .height(maxWidth)
                    // Clips the scaled/panned content below to this Box's
                    // own (un-scaled) bounds. This has to live here, on the
                    // outer, fixed-size container -- graphicsLayer's own
                    // `clip` parameter (see below) only clips a layer's
                    // content to *that layer's own* local, pre-transform
                    // bounds, which does nothing when the content already
                    // exactly fills that local space; it does not clip the
                    // transformed/scaled layer itself to its parent.
                    .clipToBounds()
                    // A single gesture loop decides tap vs. pinch/pan itself
                    // (rather than stacking detectTapGestures and
                    // detectTransformGestures as two independent detectors,
                    // which is a known source of double-firing/conflicts
                    // when both watch the same pointer stream). Below the
                    // touch-slop threshold and with only one pointer down,
                    // a release commits as a tap; past that threshold, or
                    // with a second pointer down, it commits as zoom/pan.
                    .pointerInput(displayScale, mode) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val touchSlop = viewConfiguration.touchSlop
                            var isTransforming = false
                            var accumulatedPan = Offset.Zero
                            var event: PointerEvent
                            do {
                                event = awaitPointerEvent()
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                if (!isTransforming) {
                                    accumulatedPan += panChange
                                    val zoomedEnough = kotlin.math.abs(zoomChange - 1f) > 0.01f
                                    val pannedEnough = accumulatedPan.getDistance() > touchSlop
                                    if (zoomedEnough || pannedEnough || event.changes.size > 1) {
                                        isTransforming = true
                                    }
                                }

                                if (isTransforming) {
                                    val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                    zoom = newZoom
                                    panOffset = Offset(
                                        clampPan(panOffset.x + panChange.x, boxSizePx, newZoom),
                                        clampPan(panOffset.y + panChange.y, boxSizePx, newZoom)
                                    )
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })

                            if (!isTransforming) {
                                val (imageX, imageY) = screenTapToImageCoords(
                                    screenX = down.position.x,
                                    screenY = down.position.y,
                                    boxSizePx = boxSizePx,
                                    displayScale = displayScale,
                                    zoom = zoom,
                                    panX = panOffset.x,
                                    panY = panOffset.y
                                )
                                applyTap(imageX, imageY)
                            }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom,
                            translationX = panOffset.x,
                            translationY = panOffset.y
                        )
                ) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = "Rice sample",
                        modifier = Modifier.fillMaxSize()
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        grains.forEach { box ->
                            val topLeft = Offset(box.x * displayScale, box.y * displayScale)
                            val boxSize = Size(box.width * displayScale, box.height * displayScale)
                            when (box.action) {
                                "removed" -> drawRect(
                                    color = Color.White.copy(alpha = 0.85f),
                                    topLeft = topLeft,
                                    size = boxSize,
                                    style = Stroke(
                                        width = 1.5.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                                    )
                                )
                                "added" -> {
                                    // Accent900 is a very dark green -- easy
                                    // to lose against a dark patch of the
                                    // photo without some halo. Removing it
                                    // entirely (previous pass) made added
                                    // boxes hard to see; this brings a much
                                    // smaller one back (1dp vs. the original
                                    // 2dp) so the box is still close to the
                                    // same footprint as detected/removed.
                                    val halo = 1.dp.toPx()
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.7f),
                                        topLeft = Offset(topLeft.x - halo, topLeft.y - halo),
                                        size = Size(boxSize.width + halo * 2, boxSize.height + halo * 2),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                    drawRect(
                                        color = Accent900,
                                        topLeft = topLeft,
                                        size = boxSize,
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                }
                                else -> drawRect(
                                    color = DetectionBlue,
                                    topLeft = topLeft,
                                    size = boxSize,
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }
                        }
                    }
                }

                // Outside the graphicsLayer Box above, so these stay a
                // fixed size/position regardless of the image's zoom.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FloatingZoomButton(symbol = "+", onClick = { stepZoom(ZOOM_STEP, boxSizePx) })
                    FloatingZoomButton(symbol = "−", onClick = { stepZoom(1f / ZOOM_STEP, boxSizePx) })
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendEntry(
                label = "Detected $aiCount",
                swatch = {
                    Box(
                        Modifier
                            .size(12.dp)
                            .border(1.5.dp, DetectionBlue)
                    )
                }
            )
            LegendEntry(
                label = "Removed $removedCount",
                swatch = {
                    Box(
                        Modifier
                            .size(12.dp)
                            .border(1.5.dp, Neutral500)
                    )
                }
            )
            LegendEntry(
                label = "Added $addedCount",
                swatch = {
                    Box(
                        Modifier
                            .size(12.dp)
                            .border(1.5.dp, Accent900)
                    )
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModeSegmentedControl(
                mode = mode,
                onModeChange = { mode = it },
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it },
                singleLine = true,
                shape = RectangleShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text("g", fontSize = 12.sp, color = Neutral700) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = DividerColor
                ),
                modifier = Modifier.width(150.dp)
            )
        }

        if (weightText.isNotBlank() && !isValidWeightValue(weightText)) {
            Text(
                "Enter a valid weight in grams (e.g. 12.45)",
                fontSize = 12.sp,
                color = com.grainmvp.android.ui.theme.ErrorRed,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = DividerColor)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SecondaryActionButton(
                text = "New photo",
                onClick = onRetakePhoto,
                modifier = Modifier.weight(1f)
            )
            PrimaryActionButton(
                text = "Confirm $confirmedCount",
                enabled = isValidWeightValue(weightText),
                onClick = { onSubmit(prepareGrainsForSubmission(grains), weightText) },
                height = 52.dp,
                modifier = Modifier.weight(1.4f)
            )
        }
    }
}

/**
 * Discrete zoom in/out step over the image. Smaller and less
 * conspicuous than the mode-toggle buttons this replaced (44dp) --
 * pinch is the primary way to zoom, so these are a secondary,
 * lower-emphasis control, not a primary action.
 */
@Composable
private fun FloatingZoomButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LegendEntry(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        swatch()
        Text(label, fontSize = 12.sp, color = Neutral700)
    }
}

@Composable
private fun ModeSegmentedControl(
    mode: GrainCorrectionMode,
    onModeChange: (GrainCorrectionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .border(1.dp, DividerColor)
    ) {
        SegmentOption(
            text = "Remove",
            selected = mode == GrainCorrectionMode.REMOVE,
            onClick = { onModeChange(GrainCorrectionMode.REMOVE) },
            modifier = Modifier.weight(1f)
        )
        SegmentOption(
            text = "Add",
            selected = mode == GrainCorrectionMode.ADD,
            onClick = { onModeChange(GrainCorrectionMode.ADD) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SegmentOption(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (selected) AccentGreen else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.White else TextPrimaryGranular,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}
