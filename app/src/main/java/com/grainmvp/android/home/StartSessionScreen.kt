package com.grainmvp.android.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grainmvp.android.R
import com.grainmvp.android.session.SessionInfo
import com.grainmvp.android.session.isValidSeason
import com.grainmvp.android.session.sampleIdFor
import com.grainmvp.android.session.isEntryInSession
import com.grainmvp.android.session.normalizeSeason
import com.grainmvp.android.ui.components.BlueprintFrame
import com.grainmvp.android.ui.components.PrimaryActionButton
import com.grainmvp.android.ui.components.SecondaryActionButton
import com.grainmvp.android.ui.theme.AccentGreen
import com.grainmvp.android.ui.theme.Accent700
import com.grainmvp.android.ui.theme.DividerColor
import com.grainmvp.android.ui.theme.ErrorRed
import com.grainmvp.android.ui.theme.Neutral600
import com.grainmvp.android.ui.theme.Neutral700
import com.grainmvp.android.ui.theme.SurfaceBg
import com.grainmvp.android.ui.theme.TextPrimaryGranular

/**
 * Screen 02 (Start session) of the GRANULAR field redesign.
 *
 * The substantive change this screen exists for: technician name and
 * entry details are asked here, right after the opening splash, instead of
 * on the old Submit screen. By the time the technician reaches Submit
 * (submit/SubmitScreen.kt), it's a read-only confirmation of these two
 * values, not a form -- see MainActivity's Screen.StartSession wiring.
 *
 * "Backend reachable" is still static display text -- there's no real
 * connectivity check in this app, and SPEC.md explicitly excludes that
 * kind of logic as out of scope. [queuedCount], though, is real: it's
 * `MainActivity`'s in-memory `replicateQueue` size, and [onSendQueued]
 * actually retries every queued submission (see `sendQueuedReplicates`
 * in MainActivity.kt). Documented in docs/IMPLEMENTATION.md.
 *
 * With [keepTechnician] (Result's "Take photo of another sample", or
 * "Keep on device") the screen becomes the Another sample form: the
 * technician is shown read-only and every other field is asked again.
 * This screen always starts sample 1. An entry (season + index number)
 * already in [sessionEntries] is blocked ([isEntryInSession]): its sample 2
 * is started from Result's "Take photo of sample 2 of 2". The sample ID is
 * not typed: it is generated from season, index number and sample number
 * ([sampleIdFor]).
 */
@Composable
fun StartSessionScreen(
    defaultTechnicianName: String,
    defaultSeason: String,
    keepTechnician: Boolean,
    sessionEntries: List<SessionInfo>,
    queuedCount: Int,
    onSendQueued: () -> Unit,
    onStartScan: (session: SessionInfo) -> Unit
) {
    var technicianName by remember { mutableStateOf(defaultTechnicianName) }
    var season by remember { mutableStateOf(defaultSeason) }
    var indexNumber by remember { mutableStateOf("") }
    var selectionName by remember { mutableStateOf("") }

    val normalizedSeason = normalizeSeason(season)
    val seasonError = if (season.isNotBlank() && !isValidSeason(normalizedSeason)) {
        "Use a 4-digit year followed by DS or WS, e.g. 2026WS"
    } else {
        null
    }
    val entryInSession = isEntryInSession(normalizedSeason, indexNumber.trim(), sessionEntries)
    val canStart = !entryInSession &&
            technicianName.isNotBlank() &&
            isValidSeason(normalizedSeason) &&
            indexNumber.isNotBlank() &&
            selectionName.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .padding(24.dp)
    ) {
        // The form scrolls so the fields and the on-screen keyboard
        // fit on small phones; the Start button below stays pinned.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.granular_logo),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp)
                )
                Text(
                    "GRANULAR",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 1.2.sp,
                    color = TextPrimaryGranular
                )
            }

            Column(modifier = Modifier.padding(top = 24.dp)) {
                Text(
                    if (keepTechnician) "Another sample" else "Rice grain quality assessment",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    lineHeight = 32.sp,
                    color = TextPrimaryGranular
                )
                Text(
                    if (keepTechnician) "Same technician. Enter the details of the next sample."
                    else "Photograph a sample, correct the detection, submit the replicate.",
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 14.sp,
                    color = Neutral700
                )
            }

            BlueprintFrame(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 18.dp
                )
            ) {
                Text(
                    if (keepTechnician) "NEXT SAMPLE" else "NEW SESSION",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.6.sp,
                    color = Accent700
                )

                if (keepTechnician) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("TECHNICIAN", fontSize = 12.sp, letterSpacing = 1.sp, color = Neutral700)
                        Text(technicianName, fontSize = 16.sp, color = TextPrimaryGranular)
                    }
                } else {
                    LabeledField(
                        label = "Technician",
                        value = technicianName,
                        onValueChange = { technicianName = it },
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }

                LabeledField(
                    label = "Season",
                    value = season,
                    onValueChange = { season = it },
                    placeholder = "e.g. 2026WS",
                    error = seasonError,
                    modifier = Modifier.padding(top = 14.dp)
                )

                LabeledField(
                    label = "Index No.",
                    value = indexNumber,
                    onValueChange = { indexNumber = it },
                    error = if (indexNumber.isNotBlank() && entryInSession) "This entry is already in this session" else null,
                    modifier = Modifier.padding(top = 14.dp)
                )

                LabeledField(
                    label = "Selection Name",
                    value = selectionName,
                    onValueChange = { selectionName = it },
                    modifier = Modifier.padding(top = 14.dp)
                )

            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PrimaryActionButton(
            text = "Start scan",
            enabled = canStart,
            onClick = {
                onStartScan(
                    SessionInfo(
                        technicianName = technicianName.trim(),
                        season = normalizedSeason,
                        indexNumber = indexNumber.trim(),
                        selectionName = selectionName.trim(),
                        sampleId = sampleIdFor(normalizedSeason, indexNumber.trim(), 1),
                        sampleNo = 1
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Backend reachable", fontSize = 12.sp, color = Neutral600)
            Text("$queuedCount queued", fontSize = 12.sp, color = Neutral600)
        }

        if (queuedCount > 0) {
            SecondaryActionButton(
                text = "Send $queuedCount queued",
                onClick = onSendQueued,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            label.uppercase(),
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = Neutral700
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = error != null,
            placeholder = placeholder?.let { { Text(it, color = Neutral600) } },
            supportingText = error?.let { { Text(it, color = ErrorRed) } },
            shape = RectangleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = DividerColor,
                focusedTextColor = TextPrimaryGranular,
                unfocusedTextColor = TextPrimaryGranular
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )
    }
}
