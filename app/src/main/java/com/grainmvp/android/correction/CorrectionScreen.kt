package com.grainmvp.android.correction

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.ui.components.SecondaryActionButton
import com.grainmvp.android.ui.components.PrimaryActionButton
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

/**
 * Screen 05 (Review & correct) of the GRANULAR field redesign.
 *
 * Replaces the old "the weak screen today" version with:
 *  - a live running count: AI detected vs. you removed/added, computed
 *    reactively as confirmedCount = initialGrains.size - removed + added
 *  - an explicit Remove/Add mode (segmented control + floating +/-
 *    buttons over the image), so a tap is never ambiguous about what it
 *    will do -- see [GrainCorrectionMode] / [applyGrainTap] in
 *    CorrectionLogic.kt
 *  - box states distinguished by line style, not just color, for
 *    colorblind accessibility: solid blue = detected/untouched, white
 *    dashed = removed, thick dark with a white halo = added
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
                .border(width = 1.dp, color = DividerColor)
        ) {
            val displayScale = constraints.maxWidth.toFloat() / IMAGE_SIZE

            Box(
                modifier = Modifier
                    .width(maxWidth)
                    .height(maxWidth)
                    .pointerInput(displayScale, mode) {
                        detectTapGestures { tapOffset ->
                            applyTap(tapOffset.x / displayScale, tapOffset.y / displayScale)
                        }
                    }
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
                                val halo = 2.dp.toPx()
                                drawRect(
                                    color = Color.White.copy(alpha = 0.8f),
                                    topLeft = Offset(topLeft.x - halo, topLeft.y - halo),
                                    size = Size(boxSize.width + halo * 2, boxSize.height + halo * 2),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                                drawRect(
                                    color = Accent900,
                                    topLeft = topLeft,
                                    size = boxSize,
                                    style = Stroke(width = 2.dp.toPx())
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

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FloatingModeButton(symbol = "+", onClick = { mode = GrainCorrectionMode.ADD })
                    FloatingModeButton(symbol = "−", onClick = { mode = GrainCorrectionMode.REMOVE })
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
                            .border(2.dp, Accent900)
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
                modifier = Modifier.weight(1.4f)
            )
        }
    }
}

@Composable
private fun FloatingModeButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = Color.White, fontSize = 20.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold)
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
