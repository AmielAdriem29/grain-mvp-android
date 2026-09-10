package com.grainmvp.android

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grainmvp.android.camera.CameraPreviewScreen
import com.grainmvp.android.correction.CorrectionScreen
import com.grainmvp.android.dev.FAKE_GRAINS_FOR_DEV_ONLY
import com.grainmvp.android.home.StartSessionScreen
import com.grainmvp.android.home.WelcomeScreen
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.network.NetworkTestScreen
import com.grainmvp.android.session.incrementSampleId
import com.grainmvp.android.submit.QueuedReplicate
import com.grainmvp.android.submit.SubmitScreen
import com.grainmvp.android.submit.submitQueuedReplicate
import com.grainmvp.android.ui.theme.GrainMvpTheme
import kotlinx.coroutines.launch

/**
 * Entry point. Screens are swapped in per phase branch; as of the
 * GRANULAR field redesign (2026-09), the flow is:
 *  - Welcome (opening splash)
 *  - Start Session (technician + sample ID -- collected once here, not
 *    on Submit anymore)
 *  - Capture -> Correction -> Submit, all carrying technicianName/
 *    sampleId through as plain parameters
 *  - Submit's Result screen's "Next sample" skips straight back to
 *    Capture with the same technician and an incremented sample ID,
 *    bypassing Start Session
 *  - A submission that fails to send can be "kept on device" from the
 *    Failure screen -- held in `replicateQueue` (in-memory only) and
 *    retried manually from Start Session's "Send N queued" -- see
 *    submit/ReplicateQueue.kt and [sendQueuedReplicates] below
 *
 * Screen state is a simple sealed class rather than a real navigation
 * library, since there are still only a handful of screens.
 */
private sealed class Screen {
    data object Welcome : Screen()
    data object StartSession : Screen()
    data class Capture(val technicianName: String, val sampleId: String) : Screen()
    data class Correction(val image: Bitmap, val technicianName: String, val sampleId: String) : Screen()
    data class Submit(
        val image: Bitmap,
        val aiPredictedGrains: List<GrainBox>,
        val confirmedGrains: List<GrainBox>,
        val weight: String,
        val technicianName: String,
        val sampleId: String
    ) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GrainMvpApp()
        }
    }
}

@Composable
fun GrainMvpApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Welcome) }

    // Simple in-memory default (no persistence needed) so returning to
    // Start Session after ending a session pre-fills the last-used
    // technician name, per the redesign brief.
    var lastTechnicianName by remember { mutableStateOf("") }

    // Submissions that failed to send and are being held for a later
    // manual retry ("Keep on device" on the Failure screen) -- see
    // submit/ReplicateQueue.kt. In-memory only for now: a deliberate
    // scope decision for this first version, not an oversight. Lost if
    // the app is killed; a disk-backed version that survives an app
    // restart is tracked as follow-up work.
    val replicateQueue = remember { mutableStateListOf<QueuedReplicate>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // TEMPORARY debug toggle to reach NetworkTestScreen (throwaway,
    // see network/NetworkTestScreen.kt). Only available from the
    // Capture screen. Remove this toggle once real navigation exists
    // and Phase 2 is verified -- this is not part of the real app flow.
    var showNetworkTest by remember { mutableStateOf(false) }

    GrainMvpTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val currentScreen = screen) {
                is Screen.Welcome -> {
                    WelcomeScreen(onStart = { screen = Screen.StartSession })
                }

                is Screen.StartSession -> {
                    StartSessionScreen(
                        defaultTechnicianName = lastTechnicianName,
                        queuedCount = replicateQueue.size,
                        onSendQueued = {
                            scope.launch { sendQueuedReplicates(context, replicateQueue) }
                        },
                        onStartScan = { technicianName, sampleId ->
                            lastTechnicianName = technicianName
                            screen = Screen.Capture(technicianName, sampleId)
                        }
                    )
                }

                is Screen.Capture -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (showNetworkTest) {
                            NetworkTestScreen()
                        } else {
                            CameraPreviewScreen(
                                technicianName = currentScreen.technicianName,
                                sampleId = currentScreen.sampleId,
                                onImageConfirmed = { bitmap ->
                                    screen = Screen.Correction(
                                        image = bitmap,
                                        technicianName = currentScreen.technicianName,
                                        sampleId = currentScreen.sampleId
                                    )
                                }
                            )
                        }

                        // Styled to match the rest of the capture chrome
                        // (GRID/LAMP: bordered dark pill, uppercase label)
                        // rather than a default Material button, since this
                        // sits directly over that same screen.
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.72f))
                                .border(1.dp, Color.White.copy(alpha = 0.35f))
                                .clickable { showNetworkTest = !showNetworkTest }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                if (showNetworkTest) "CAMERA" else "NETWORK TEST",
                                color = Color.White,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }

                is Screen.Correction -> {
                    CorrectionScreen(
                        image = currentScreen.image,
                        // TEMPORARY: real predicted grains come from Phase 2's
                        // predict() once verified against a real backend. See
                        // dev/FakeGrainsForDev.kt -- delete this fake data and
                        // call the real endpoint instead once Sitoy's server
                        // is reachable.
                        initialGrains = FAKE_GRAINS_FOR_DEV_ONLY,
                        onRetakePhoto = {
                            screen = Screen.Capture(currentScreen.technicianName, currentScreen.sampleId)
                        },
                        onSubmit = { confirmedGrains, weight ->
                            screen = Screen.Submit(
                                image = currentScreen.image,
                                // The "unmodified original AI list" the spec
                                // requires for aiPredictedGrains is exactly
                                // what Correction started from -- currently
                                // the same fake data, until Phase 2 is real.
                                aiPredictedGrains = FAKE_GRAINS_FOR_DEV_ONLY,
                                confirmedGrains = confirmedGrains,
                                weight = weight,
                                technicianName = currentScreen.technicianName,
                                sampleId = currentScreen.sampleId
                            )
                        }
                    )
                }

                is Screen.Submit -> {
                    SubmitScreen(
                        technicianName = currentScreen.technicianName,
                        sampleId = currentScreen.sampleId,
                        image = currentScreen.image,
                        aiPredictedGrains = currentScreen.aiPredictedGrains,
                        confirmedGrains = currentScreen.confirmedGrains,
                        weight = currentScreen.weight,
                        onEndSession = { screen = Screen.Welcome },
                        onNextSample = { technicianName, nextSampleId ->
                            lastTechnicianName = technicianName
                            screen = Screen.Capture(technicianName, nextSampleId)
                        },
                        onKeepOnDevice = { queuedItem ->
                            replicateQueue.add(queuedItem)
                            lastTechnicianName = queuedItem.technicianName
                            // "...or keep scanning and send it later" (the
                            // Failure screen's own copy) means continuing to
                            // the next sample, not ending the session --
                            // same shape as Result's "Next sample": same
                            // technician, incremented ID, straight back to
                            // Capture.
                            screen = Screen.Capture(
                                queuedItem.technicianName,
                                incrementSampleId(queuedItem.sampleId)
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Attempts to send every currently-queued replicate, in the order they
 * were queued, removing each one as soon as it succeeds. A failure
 * (still no connection, or anything else) just leaves that item queued
 * -- there's no retry backoff or scheduling here, since this is a
 * manual, technician-triggered retry ("Send N queued" on Start
 * Session), not a background job.
 */
private suspend fun sendQueuedReplicates(context: Context, queue: MutableList<QueuedReplicate>) {
    val toSend = queue.toList()
    if (toSend.isEmpty()) return

    var sentCount = 0
    for (item in toSend) {
        try {
            submitQueuedReplicate(item)
            queue.remove(item)
            sentCount++
        } catch (e: Exception) {
            // Leave it queued; the technician can tap "Send" again once
            // they actually have a connection.
        }
    }

    val remaining = queue.size
    val message = when {
        remaining == 0 -> "Sent $sentCount queued sample${if (sentCount == 1) "" else "s"}."
        sentCount == 0 -> "Still couldn't send $remaining queued sample${if (remaining == 1) "" else "s"}."
        else -> "Sent $sentCount, $remaining still queued."
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
