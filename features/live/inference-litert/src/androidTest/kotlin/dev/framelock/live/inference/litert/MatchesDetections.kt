package dev.framelock.live.inference.litert

import dev.framelock.live.domain.inference.Detection

internal const val MIN_CONFIDENCE = 0.5f
internal const val MIN_IOU = 0.8f

/** Finds a one-to-one match, independent of score ordering and extra detections. */
internal fun matchesDetections(
    references: List<ReferenceDetection>,
    detections: List<Detection>,
): Boolean {
    require(references.isNotEmpty()) { "A detection fixture needs at least one reference" }
    val candidates = detections.filter { it.score >= MIN_CONFIDENCE }
    val owners = IntArray(candidates.size) { -1 }

    fun assign(
        reference: Int,
        visited: BooleanArray,
    ): Boolean {
        val expected = references[reference]
        candidates.forEachIndexed { index, candidate ->
            if (visited[index] || candidate.label != expected.label ||
                intersectionOverUnion(candidate.box, expected.box) < MIN_IOU
            ) {
                return@forEachIndexed
            }
            visited[index] = true
            if (owners[index] < 0 || assign(owners[index], visited)) {
                owners[index] = reference
                return true
            }
        }
        return false
    }

    return references.indices.all { assign(it, BooleanArray(candidates.size)) }
}
