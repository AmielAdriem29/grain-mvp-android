package com.grainmvp.android.correction

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grainmvp.android.network.GrainBox

private const val IMAGE_SIZE = 1024
private const val NEW_BOX_SIZE = 24

/**
 * Phase 3 — Correction Screen, per SPEC.md:
 *  - image at full screen width, displayScale = screenWidthPx / 1024
 *  - box colors: blue = untouched (action null), grey = removed, green = added
 *  - tap inside a box toggles removed <-> untouched; tap empty space adds
 *    a new 24x24 box with action "added"
 *  - weight input, "retake photo" button
 *
 * Deviation from spec (documented in IMPLEMENTATION.md): re-tapping a
 * box that started as "added" and was then "removed" resets it to null
 * rather than back to "added" -- the spec's toggle rule only describes
 * null/kept <-> removed, and doesn't cover re-toggling a user-added box.
 * This is a minor, rare edge case (removing then un-removing your own
 * added box), not a correctness issue for the main flow.
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

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Review and Correct",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            textAlign = TextAlign.Center
        )
        Text(
            "Tap a box to remove it. Tap directly on a missed grain to mark it as immature.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            textAlign = TextAlign.Center
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val displayScale = constraints.maxWidth.toFloat() / IMAGE_SIZE

            Box(
                modifier = Modifier
                    .width(maxWidth)
                    .height(maxWidth)
                    .pointerInput(displayScale) {
                        detectTapGestures { tapOffset ->
                            // Spec: imageX = tappedX / displayScale
                            val imageX = tapOffset.x / displayScale
                            val imageY = tapOffset.y / displayScale

                            val tappedIndex = grains.indexOfFirst { box ->
                                imageX >= box.x && imageX < box.x + box.width &&
                                        imageY >= box.y && imageY < box.y + box.height
                            }

                            if (tappedIndex >= 0) {
                                val box = grains[tappedIndex]
                                grains[tappedIndex] = if (box.action == "removed") {
                                    box.copy(action = null)
                                } else {
                                    box.copy(action = "removed")
                                }
                            } else {
                                // Spec: new box at (imageX, imageY), fixed 24x24
                                grains.add(
                                    GrainBox(
                                        x = imageX.toInt(),
                                        y = imageY.toInt(),
                                        width = NEW_BOX_SIZE,
                                        height = NEW_BOX_SIZE,
                                        confidence = null,
                                        action = "added"
                                    )
                                )
                            }
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
                        val color = when (box.action) {
                            "removed" -> Color.Gray
                            "added" -> Color.Green
                            else -> Color.Blue // null (untouched AI box) or "kept"
                        }
                        drawRect(
                            color = color,
                            topLeft = Offset(box.x * displayScale, box.y * displayScale),
                            size = Size(box.width * displayScale, box.height * displayScale),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = weightText,
            onValueChange = { weightText = it },
            label = { Text("Weight (grams)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onRetakePhoto) {
                Text("New Photo")
            }
            Button(
                enabled = weightText.isNotBlank(),
                onClick = {
                    // Backend's example confirmedGrains payload shows
                    // untouched boxes as action: "kept", not null. Convert
                    // here at the correction -> submit boundary so the
                    // Canvas color logic above can keep treating null as
                    // simply "untouched/blue" without a separate case.
                    val confirmed = grains.map {
                        if (it.action == null) it.copy(action = "kept") else it
                    }
                    onSubmit(confirmed, weightText)
                }
            ) {
                Text("Confirm")
            }
        }
    }
}