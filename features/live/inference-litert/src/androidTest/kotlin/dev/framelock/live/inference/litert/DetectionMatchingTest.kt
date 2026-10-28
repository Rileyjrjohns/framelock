package dev.framelock.live.inference.litert

import dev.framelock.live.domain.inference.Detection
import dev.framelock.live.domain.inference.LabelId
import dev.framelock.live.domain.inference.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionMatchingTest {
    private val left = NormalizedRect(0f, 0f, 0.4f, 1f)
    private val right = NormalizedRect(0.6f, 0f, 1f, 1f)
    private val references = listOf(reference(left), reference(right))

    @Test
    fun orderTiesAndExtraDetectionsDoNotChangeTheMatch() {
        val detections = listOf(detection(right), detection(left), detection(left, label = 3))
        assertTrue(matchesDetections(references, detections))
        assertTrue(matchesDetections(references, detections.reversed()))
    }

    @Test
    fun missingWrongClassAndLowConfidenceDetectionsFail() {
        assertFalse(matchesDetections(references, listOf(detection(left))))
        assertFalse(matchesDetections(references, listOf(detection(left), detection(right, label = 3))))
        assertFalse(matchesDetections(references, listOf(detection(left), detection(right, score = 0.499f))))
        assertTrue(matchesDetections(references, listOf(detection(left, score = 0.5f), detection(right, score = 0.5f))))
    }

    @Test
    fun aDetectionCannotSatisfyTwoReferences() {
        assertFalse(matchesDetections(listOf(reference(left), reference(left)), listOf(detection(left))))
    }

    @Test
    fun overlappingCandidatesAreReassignedInsteadOfDependingOnOrder() {
        val full = NormalizedRect(0f, 0f, 1f, 1f)
        val narrow = NormalizedRect(0f, 0f, 0.8f, 1f)
        val shifted = NormalizedRect(0.1f, 0f, 1f, 1f)
        val expected = listOf(reference(full), reference(narrow))
        assertTrue(matchesDetections(expected, listOf(detection(full), detection(shifted))))
        assertTrue(matchesDetections(expected.reversed(), listOf(detection(shifted), detection(full))))
    }

    @Test
    fun overlapUsesAllFourEdgesAndTheWholeUnion() {
        val full = NormalizedRect(0f, 0f, 1f, 1f)
        assertEquals(1f, intersectionOverUnion(full, full), 0f)
        assertEquals(0f, intersectionOverUnion(left, right), 0f)
        assertEquals(0f, intersectionOverUnion(full, NormalizedRect(0f, 0f, 0f, 0f)), 0f)
        assertTrue(matchesDetections(listOf(reference(full)), listOf(detection(NormalizedRect(0f, 0f, 0.8f, 1f)))))
        assertFalse(matchesDetections(listOf(reference(full)), listOf(detection(NormalizedRect(0f, 0f, 0.79f, 1f)))))
        assertFalse(matchesDetections(listOf(reference(full)), listOf(detection(NormalizedRect(0f, 0f, 1f, 0.79f)))))
    }

    @Test
    fun anEmptyReferenceCannotPass() {
        assertThrows(IllegalArgumentException::class.java) { matchesDetections(emptyList(), listOf(detection(left))) }
    }

    private fun reference(box: NormalizedRect) = ReferenceDetection(LabelId(0), box)

    private fun detection(
        box: NormalizedRect,
        label: Int = 0,
        score: Float = 0.8f,
    ) = Detection(box, LabelId(label), score)
}
