package com.grainmvp.android.correction

import org.junit.Assert.assertEquals
import org.junit.Test

/** The grade the phone calculates when GET /api/grade can't be reached. Must match backend CalculationService. */
class GradePreviewTest {

    @Test
    fun `phone grade is marked as not from the server`() {
        assertEquals(GradePreview("Pr", 1.6, fromServer = false), localGradePreview("0.47"))
    }

    @Test
    fun `percentage is weight over 30 g, rounded to one decimal`() {
        assertEquals(10.0, localGradePreview("3").percentage, 0.0)
        assertEquals(100.0, localGradePreview("30").percentage, 0.0)
    }

    @Test
    fun `a midpoint percentage rounds away from zero, like the backend`() {
        // 0.135 / 30 * 100 = 0.45 exactly.
        assertEquals(0.5, localGradePreview("0.135").percentage, 0.0)
        // 4.515 / 30 * 100 = 15.05 exactly.
        assertEquals(15.1, localGradePreview("4.515").percentage, 0.0)
    }

    @Test
    fun `the grade comes from the rounded percentage, like the backend`() {
        // 0.59 g is 1.966...%, which rounds to 2.0%: G1, not Pr.
        assertEquals("G1", localGradePreview("0.59").grade)
    }

    @Test
    fun `grade boundaries match the backend bands`() {
        assertEquals("Pr", localGradePreview("0.57").grade)    // 1.9%
        assertEquals("G1", localGradePreview("0.6").grade)     // 2.0%
        assertEquals("G1", localGradePreview("1.5").grade)     // 5.0%
        assertEquals("G2", localGradePreview("3").grade)       // 10.0%
        assertEquals("G3", localGradePreview("4.5").grade)     // 15.0%
    }

    @Test
    fun `the top grade uses the backend's spelling`() {
        assertEquals("Not Classified", localGradePreview("4.53").grade) // 15.1%
    }
}
