package dev.framelock.telemetry.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RoomTelemetryBufferTest {
    private lateinit var database: TelemetryDatabase

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TelemetryDatabase::class.java).build()
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun theCapDropsTheOldestAndCountsThem() =
        runTest {
            val reported = mutableListOf<Int>()
            val buffer = RoomTelemetryBuffer(database.dao(), maxRows = 5) { reported += it }

            buffer.append(List(4) { it.toLong() to "{\"n\":$it}" })
            buffer.append(List(4) { (4 + it).toLong() to "{\"n\":${4 + it}}" })

            assertEquals(5, buffer.count())
            assertEquals(3, buffer.droppedByCap)
            assertEquals(listOf(3), reported)
            assertEquals(List(5) { "{\"n\":${3 + it}}" }, buffer.oldest(10).map { it.payload })
        }

    @Test
    fun deletedEventsDoNotComeBack() =
        runTest {
            val buffer = RoomTelemetryBuffer(database.dao())
            buffer.append(List(3) { it.toLong() to "{\"n\":$it}" })

            buffer.delete(buffer.oldest(2).map { it.id })

            assertEquals(listOf("{\"n\":2}"), buffer.oldest(10).map { it.payload })
        }
}
