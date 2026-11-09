package com.grainmvp.android.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonValidationTest {

    @Test
    fun `wet and dry season codes are valid`() {
        assertTrue(isValidSeason("2026WS"))
        assertTrue(isValidSeason("2026DS"))
    }

    @Test
    fun `empty is invalid`() {
        assertFalse(isValidSeason(""))
    }

    @Test
    fun `year without a season is invalid`() {
        assertFalse(isValidSeason("2026"))
    }

    @Test
    fun `a year that is not 4 digits is invalid`() {
        assertFalse(isValidSeason("26WS"))
        assertFalse(isValidSeason("20266WS"))
        assertFalse(isValidSeason("202WS"))
    }

    @Test
    fun `an unknown season letter pair is invalid`() {
        assertFalse(isValidSeason("2026XS"))
        assertFalse(isValidSeason("2026WD"))
    }

    @Test
    fun `lower case is invalid until normalized`() {
        assertFalse(isValidSeason("2026ws"))
    }

    @Test
    fun `extra characters or spaces are invalid`() {
        assertFalse(isValidSeason("2026WSX"))
        assertFalse(isValidSeason(" 2026WS"))
        assertFalse(isValidSeason("2026WS "))
        assertFalse(isValidSeason("WS2026"))
    }

    @Test
    fun `normalize trims and upper-cases`() {
        assertEquals("2026WS", normalizeSeason("  2026ws "))
        assertTrue(isValidSeason(normalizeSeason("2026ds")))
    }
}
