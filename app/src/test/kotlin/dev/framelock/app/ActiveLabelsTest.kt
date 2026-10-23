package dev.framelock.app

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.live.domain.inference.LabelId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ActiveLabelsTest {
    private var active: ModelVersion? = null
    private val reads = mutableListOf<String>()
    private val failures = mutableListOf<Throwable>()
    private val byVersion = mapOf("a" to listOf("person", "bicycle"), "b" to listOf("cat"))

    private val labels =
        ActiveLabels(
            activeVersion = { active },
            read = {
                reads += it.value
                byVersion[it.value] ?: error("unreadable")
            },
            onUnreadable = { failures += it },
        )

    @Test
    fun `before any refresh, and with no active model, labels are class numbers`() {
        labels.refresh()

        assertEquals("#1", labels.labelOf(LabelId(1)))
    }

    @Test
    fun `labels of the active model are cached and read once per version`() {
        active = ModelVersion("a")

        labels.refresh()
        labels.refresh()

        assertEquals("bicycle", labels.labelOf(LabelId(1)))
        assertEquals("#2", labels.labelOf(LabelId(2)))
        assertEquals(listOf("a"), reads)
    }

    @Test
    fun `a swap is picked up by the next refresh, not before`() {
        active = ModelVersion("a")
        labels.refresh()

        active = ModelVersion("b")
        assertEquals("person", labels.labelOf(LabelId(0)))
        labels.refresh()

        assertEquals("cat", labels.labelOf(LabelId(0)))
    }

    @Test
    fun `unreadable labels are reported and the overlay falls back to numbers`() {
        active = ModelVersion("missing")

        labels.refresh()

        assertEquals("#0", labels.labelOf(LabelId(0)))
        assertEquals(1, failures.size)
    }
}
