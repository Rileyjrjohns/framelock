package dev.framelock.telemetry.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import dev.framelock.telemetry.domain.TelemetryBatch
import dev.framelock.telemetry.domain.port.TelemetryUploader
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TelemetryUploadWorkerTest {
    private class FakeUploader(
        var accept: Boolean,
    ) : TelemetryUploader {
        val batches = mutableListOf<TelemetryBatch>()

        override suspend fun upload(batch: TelemetryBatch): Boolean {
            batches += batch
            return accept
        }
    }

    private lateinit var context: Context
    private lateinit var database: TelemetryDatabase
    private lateinit var buffer: RoomTelemetryBuffer

    @Before
    fun open() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, TelemetryDatabase::class.java).build()
        buffer = RoomTelemetryBuffer(database.dao())
    }

    @After
    fun close() {
        database.close()
    }

    private fun worker(uploader: TelemetryUploader) =
        TestListenableWorkerBuilder<TelemetryUploadWorker>(context)
            .setWorkerFactory(TelemetryWorkerFactory(TelemetryUploadRun(buffer, uploader)))
            .build()

    @Test
    fun aSuccessfulUploadDeletesTheBatchInOnePageOfAtMost500() =
        runTest {
            buffer.append(List(750) { it.toLong() to "{\"type\":\"AppSession\",\"atMillis\":$it}" })
            val uploader = FakeUploader(accept = true)

            val result = worker(uploader).doWork()

            assertEquals(Result.success(), result)
            assertEquals(listOf(500, 250), uploader.batches.map { it.eventCount })
            assertEquals(0, buffer.count())
        }

    @Test
    fun aFailedUploadKeepsTheRowsAndAsksForARetry() =
        runTest {
            buffer.append(List(3) { it.toLong() to "{\"type\":\"AppSession\",\"atMillis\":$it}" })

            val result = worker(FakeUploader(accept = false)).doWork()

            assertEquals(Result.retry(), result)
            assertEquals(3, buffer.count())
        }

    @Test
    fun anEmptyBufferSucceedsWithoutUploading() =
        runTest {
            val uploader = FakeUploader(accept = true)

            val result = worker(uploader).doWork()

            assertEquals(Result.success(), result)
            assertEquals(0, uploader.batches.size)
        }
}
