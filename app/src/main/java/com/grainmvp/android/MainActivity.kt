package com.grainmvp.android

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grainmvp.android.camera.CameraPreviewScreen
import com.grainmvp.android.correction.CorrectionScreen
import com.grainmvp.android.detect.DetectScreen
import com.grainmvp.android.home.StartSessionScreen
import com.grainmvp.android.home.WelcomeScreen
import com.grainmvp.android.network.GrainBox
import com.grainmvp.android.network.SupabaseAuth
import com.grainmvp.android.session.SessionInfo
import com.grainmvp.android.submit.QueuedReplicate
import com.grainmvp.android.submit.isDuplicateSampleError
import com.grainmvp.android.submit.SubmitScreen
import com.grainmvp.android.submit.submitQueuedReplicate
import com.grainmvp.android.ui.theme.GrainMvpTheme
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Entry point. Screens are swapped in per phase branch; as of the
 * GRANULAR field redesign (2026-09), the flow is:
 *  - Welcome (opening splash)
 *  - Start Session (technician, season, index number, selection name and
 *    sample ID -- collected here, not on Submit)
 *  - Capture -> Detect -> Correction -> Submit, all carrying the same
 *    [SessionInfo], plus the time the photo was captured
 *  - Result's "Add second sample" goes straight back to Capture for
 *    sample 2 of the same entry; "Next sample" returns to Start Session
 *    with technician, season and the next sample ID prefilled
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
    data class Capture(val session: SessionInfo) : Screen()
    data class Detecting(val image: Bitmap, val session: SessionInfo, val capturedAt: Instant) :
        Screen()

    data class Correction(
        val image: Bitmap,
        val predictedGrains: List<GrainBox>,
        val session: SessionInfo,
        val capturedAt: Instant
    ) : Screen()

    data class Submit(
        val image: Bitmap,
        val aiPredictedGrains: List<GrainBox>,
        val confirmedGrains: List<GrainBox>,
        val weight: String,
        val session: SessionInfo,
        val capturedAt: Instant
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

private sealed class AuthState {
    data object Loading : AuthState()
    data object Ready : AuthState()
    data class Failed(val message: String) : AuthState()
}

@Composable
fun GrainMvpApp() {
    var authState by remember { mutableStateOf<AuthState>(AuthState.Loading) }
    var authAttempt by remember { mutableStateOf(0) }

    LaunchedEffect(authAttempt) {
        authState = AuthState.Loading
        authState = try {
            SupabaseAuth.ensureSignedIn()
            AuthState.Ready
        } catch (e: Exception) {
            AuthState.Failed("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    if (authState !is AuthState.Ready) {
        GrainMvpTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (val state = authState) {
                        is AuthState.Failed -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("Couldn't connect: ${state.message}")
                            Button(onClick = { authAttempt++ }) { Text("Retry") }
                        }

                        else -> CircularProgressIndicator()
                    }
                }
            }
        }
        return
    }

    var screen by remember { mutableStateOf<Screen>(Screen.Welcome) }

    // Simple in-memory default (no persistence needed) so returning to
    // Start Session after ending a session pre-fills the last-used
    // technician name, per the redesign brief.
    var lastTechnicianName by remember { mutableStateOf("") }
    var lastSeason by remember { mutableStateOf("") }
    var lastSampleId by remember { mutableStateOf("") }

    // Submissions that failed to send and are being held for a later
    // manual retry ("Keep on device" on the Failure screen) -- see
    // submit/ReplicateQueue.kt. In-memory only for now: a deliberate
    // scope decision for this first version, not an oversight. Lost if
    // the app is killed; a disk-backed version that survives an app
    // restart is tracked as follow-up work.
    val replicateQueue = remember { mutableStateListOf<QueuedReplicate>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    GrainMvpTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val currentScreen = screen) {
                is Screen.Welcome -> {
                    WelcomeScreen(onStart = { screen = Screen.StartSession })
                }

                is Screen.StartSession -> {
                    StartSessionScreen(
                        defaultTechnicianName = lastTechnicianName,
                        defaultSeason = lastSeason,
                        defaultSampleId = lastSampleId,
                        queuedCount = replicateQueue.size,
                        onSendQueued = {
                            scope.launch { sendQueuedReplicates(context, replicateQueue) }
                        },
                        onStartScan = { session ->
                            lastTechnicianName = session.technicianName
                            lastSeason = session.season
                            lastSampleId = session.sampleId
                            screen = Screen.Capture(session)
                        }
                    )
                }

                is Screen.Capture -> {
                    CameraPreviewScreen(
                        sampleId = currentScreen.session.sampleId,
                        onImageConfirmed = { bitmap, capturedAt ->
                            screen = Screen.Detecting(
                                image = bitmap,
                                session = currentScreen.session,
                                capturedAt = capturedAt
                            )
                        }
                    )
                }

                is Screen.Detecting -> {
                    DetectScreen(
                        image = currentScreen.image,
                        session = currentScreen.session,
                        onDetected = { grains ->
                            screen = Screen.Correction(
                                image = currentScreen.image,
                                predictedGrains = grains,
                                session = currentScreen.session,
                                capturedAt = currentScreen.capturedAt
                            )
                        },
                        onContinueWithoutProposals = {
                            // REQ-102: detection failed, so the technician
                            // adds every box by hand. The record is then
                            // submitted with an empty aiPredictedGrains list.
                            screen = Screen.Correction(
                                image = currentScreen.image,
                                predictedGrains = emptyList(),
                                session = currentScreen.session,
                                capturedAt = currentScreen.capturedAt
                            )
                        },
                        onRetake = {
                            screen = Screen.Capture(currentScreen.session)
                        }
                    )
                }

                is Screen.Correction -> {
                    CorrectionScreen(
                        image = currentScreen.image,
                        initialGrains = currentScreen.predictedGrains,
                        onRetakePhoto = {
                            screen = Screen.Capture(currentScreen.session)
                        },
                        onSubmit = { confirmedGrains, weight ->
                            screen = Screen.Submit(
                                image = currentScreen.image,
                                // The "unmodified original AI list" the spec
                                // requires for aiPredictedGrains is exactly
                                // what Correction started from.
                                aiPredictedGrains = currentScreen.predictedGrains,
                                confirmedGrains = confirmedGrains,
                                weight = weight,
                                session = currentScreen.session,
                                capturedAt = currentScreen.capturedAt
                            )
                        }
                    )
                }

                is Screen.Submit -> {
                    SubmitScreen(
                        session = currentScreen.session,
                        image = currentScreen.image,
                        aiPredictedGrains = currentScreen.aiPredictedGrains,
                        confirmedGrains = currentScreen.confirmedGrains,
                        weight = currentScreen.weight,
                        capturedAt = currentScreen.capturedAt,
                        onEndSession = {
                            lastSampleId = ""
                            screen = Screen.Welcome
                        },
                        onAddSecondSample = { secondSession ->
                            // Same entry, sample 2: skip Start Session.
                            screen = Screen.Capture(secondSession)
                        },
                        onNextSample = { nextSession ->
                            // New entry: back to Start Session with technician,
                            // season and the next sample ID prefilled. The
                            // index number is entered again.
                            lastTechnicianName = nextSession.technicianName
                            lastSeason = nextSession.season
                            lastSampleId = nextSession.sampleId
                            screen = Screen.StartSession
                        },
                        onKeepOnDevice = { queuedItem ->
                            replicateQueue.add(queuedItem)
                            // "...or keep scanning and send it later" (the
                            // Failure screen's own copy) follows the same
                            // path as "Next sample".
                            val nextSession = queuedItem.session.nextSample()
                            lastTechnicianName = nextSession.technicianName
                            lastSeason = nextSession.season
                            lastSampleId = nextSession.sampleId
                            screen = Screen.StartSession
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
            if (isDuplicateSampleError(e)) {
                // 409: an earlier attempt for this season, index number and
                // sample number was already saved (SDD 1.6). Nothing left
                // to send, so drop it from the queue and count it as sent.
                queue.remove(item)
                sentCount++
                continue
            }
            // Leave it queued; the technician can tap "Send" again once
            // they actually have a connection.
            Log.w("MainActivity", "Queued sample ${item.session.sampleId} failed to send", e)
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
