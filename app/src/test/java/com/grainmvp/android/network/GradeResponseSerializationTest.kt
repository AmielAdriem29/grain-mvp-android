package com.grainmvp.android.network

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins the GET /api/grade response shape from backend feature/grade-preview. */
class GradeResponseSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `grade preview response decodes every field`() {
        val body = """{"weight":0.47,"percentage":1.6,"grade":"Pr","meetsRecommendedValue":true}"""
        val response = json.decodeFromString<GradeResponse>(body)
        assertEquals(0.47, response.weight, 0.0)
        assertEquals(1.6, response.percentage, 0.0)
        assertEquals("Pr", response.grade)
        assertEquals(true, response.meetsRecommendedValue)
    }

    @Test
    fun `the top grade keeps the backend's spelling`() {
        val body = """{"weight":4.53,"percentage":15.1,"grade":"Not Classified","meetsRecommendedValue":false}"""
        assertEquals("Not Classified", json.decodeFromString<GradeResponse>(body).grade)
    }

    @Test
    fun `a response without meetsRecommendedValue still decodes`() {
        val response = json.decodeFromString<GradeResponse>("""{"weight":3,"percentage":10.0,"grade":"G2"}""")
        assertEquals("G2", response.grade)
        assertNull(response.meetsRecommendedValue)
    }
}
