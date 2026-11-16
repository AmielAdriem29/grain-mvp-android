package com.grainmvp.android.network

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins the POST /api/replicate response shape from backend dev, and that an older backend still decodes. */
class ReplicateResponseSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `dev backend response decodes every replicate field`() {
        val body = """
            {"id":"abc","percentage":3.3,"grade":"G1","meetsRecommendedValue":false,
             "replicateNo":2,"replicateLabel":"S-0142-02","confidence":0.86,
             "quality":"good","reviewStatus":"accepted"}
        """.trimIndent()
        val response = json.decodeFromString<ReplicateResponse>(body)
        assertEquals(2, response.replicateNo)
        assertEquals("S-0142-02", response.replicateLabel)
        assertEquals(0.86, response.confidence!!, 0.0)
        assertEquals(ReplicateQuality.GOOD, response.quality)
        assertEquals("accepted", response.reviewStatus)
        assertEquals(true, response.isAccepted)
    }

    @Test
    fun `bad quality and null confidence decode`() {
        val body = """{"id":"abc","percentage":3.3,"grade":"G1","quality":"bad","confidence":null,"reviewStatus":"review"}"""
        val response = json.decodeFromString<ReplicateResponse>(body)
        assertEquals(ReplicateQuality.BAD, response.quality)
        assertNull(response.confidence)
        assertEquals(false, response.isAccepted)
    }

    @Test
    fun `older backend response without replicate fields still decodes`() {
        val response = json.decodeFromString<ReplicateResponse>("""{"id":"abc","percentage":3.3,"grade":"G1"}""")
        assertEquals(0, response.replicateNo)
        assertEquals("", response.replicateLabel)
        assertNull(response.quality)
        assertNull(response.reviewStatus)
        assertEquals(false, response.isAccepted)
    }
}
