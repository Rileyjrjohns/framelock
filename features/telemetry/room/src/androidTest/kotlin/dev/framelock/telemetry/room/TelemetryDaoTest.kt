package dev.framelock.telemetry.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TelemetryDaoTest {
    private lateinit var database: TelemetryDatabase
    private lateinit var dao: TelemetryDao

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TelemetryDatabase::class.java).build()
        dao = database.dao()
    }

    @After
    fun close() {
        database.close()
    }

    private fun rows(
        from: Int,
        count: Int,
    ) = List(count) { TelemetryEventEntity(recordedAtMillis = (from + it).toLong(), payload = "{\"n\":${from + it}}") }

    @Test
    fun oldestReturnsEventsInInsertionOrderUpToTheLimit() =
        runTest {
            dao.insertAll(rows(0, 10))

            val page = dao.oldest(4)

            assertEquals(listOf(0L, 1L, 2L, 3L), page.map { it.recordedAtMillis })
        }

    @Test
    fun deleteByIdsRemovesOnlyThoseRows() =
        runTest {
            dao.insertAll(rows(0, 5))
            val ids = dao.oldest(5).map { it.id }

            dao.deleteByIds(listOf(ids[0], ids[2]))

            assertEquals(listOf(1L, 3L, 4L), dao.oldest(10).map { it.recordedAtMillis })
            assertEquals(3, dao.count())
        }

    @Test
    fun insertCappedDropsTheOldestBeyondTheCapAndReturnsHowManyWentAway() =
        runTest {
            assertEquals(0, dao.insertCapped(rows(0, 8), maxRows = 10))
            val dropped = dao.insertCapped(rows(8, 5), maxRows = 10)

            assertEquals(3, dropped)
            assertEquals(10, dao.count())
            assertEquals((3L..12L).toList(), dao.oldest(100).map { it.recordedAtMillis })
        }

    @Test
    fun aBatchLargerThanTheCapKeepsItsNewestEvents() =
        runTest {
            val dropped = dao.insertCapped(rows(0, 7), maxRows = 3)

            assertEquals(4, dropped)
            assertEquals(listOf(4L, 5L, 6L), dao.oldest(10).map { it.recordedAtMillis })
        }

    @Test
    fun idsAreNeverReusedAfterADelete() =
        runTest {
            dao.insertAll(rows(0, 2))
            val first = dao.oldest(2).map { it.id }
            dao.deleteByIds(first)
            dao.insertAll(rows(2, 1))

            assertEquals(true, dao.oldest(1).single().id > first.max())
        }
}
