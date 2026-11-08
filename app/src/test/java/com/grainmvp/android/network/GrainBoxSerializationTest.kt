package com.grainmvp.android.network

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the JSON shape of GrainBox. The backend and dashboard read the
 * action as a lowercase string, so the GrainAction enum must serialize
 * to exactly those strings.
 */
class GrainBoxSerializationTest {

    private fun box(action: GrainAction?) =
        GrainBox(x = 10, y = 20, width = 24, height = 24, confidence = 0.5f, action = action)

    @Test
    fun `kept serializes as the string kept`() {
        assertEquals(
            """{"x":10,"y":20,"width":24,"height":24,"confidence":0.5,"action":"kept"}""",
            Json.encodeToString(box(GrainAction.KEPT))
        )
    }

    @Test
    fun `removed serializes as the string removed`() {
        assertEquals(
            """{"x":10,"y":20,"width":24,"height":24,"confidence":0.5,"action":"removed"}""",
            Json.encodeToString(box(GrainAction.REMOVED))
        )
    }

    @Test
    fun `added serializes as the string added`() {
        assertEquals(
            """{"x":10,"y":20,"width":24,"height":24,"confidence":0.5,"action":"added"}""",
            Json.encodeToString(box(GrainAction.ADDED))
        )
    }

    @Test
    fun `an untouched box omits the action field`() {
        assertEquals(
            """{"x":10,"y":20,"width":24,"height":24,"confidence":0.5}""",
            Json.encodeToString(box(null))
        )
    }

    @Test
    fun `backend strings decode to the matching action`() {
        val json = """{"x":10,"y":20,"width":24,"height":24,"confidence":null,"action":"removed"}"""
        assertEquals(GrainAction.REMOVED, Json.decodeFromString<GrainBox>(json).action)
    }

    @Test
    fun `a predict response box with a null action decodes to null`() {
        val json = """{"x":10,"y":20,"width":24,"height":24,"confidence":0.91,"action":null}"""
        assertNull(Json.decodeFromString<GrainBox>(json).action)
    }
}
