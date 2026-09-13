package org.opensources.courses.core.sync

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.testing.FakeSyncOperationDao
import org.opensources.courses.testing.fixedClock

class SyncQueueTest {
    private val dao = FakeSyncOperationDao()
    private val queue = SyncQueue(dao, fixedClock())

    @Test
    fun `operations are returned in the order they were made`() =
        runTest {
            queue.enqueue(SyncOperationType.CREATE_ITEM, "list", "a")
            queue.enqueue(SyncOperationType.CHECK_ITEM, "list", "a")
            queue.enqueue(SyncOperationType.DELETE_ITEM, "list", "b", remoteItemId = "uid-b")

            assertEquals(
                listOf(SyncOperationType.CREATE_ITEM, SyncOperationType.CHECK_ITEM, SyncOperationType.DELETE_ITEM),
                queue.pending().map { it.type },
            )
            assertEquals("uid-b", queue.pending().last().remoteItemId)
            assertEquals(3, queue.observePendingCount().first())
        }

    @Test
    fun `failed operations are kept with their attempt count`() =
        runTest {
            queue.enqueue(SyncOperationType.CREATE_ITEM, "list", "a")
            val id = queue.pending().single().id

            queue.fail(listOf(id), "UNREACHABLE")
            queue.fail(listOf(id), "UNREACHABLE")

            assertEquals(2, queue.pending().single().attemptCount)
            assertTrue(queue.hasPendingForItem("a"))
        }

    @Test
    fun `confirmed operations leave the queue`() =
        runTest {
            queue.enqueue(SyncOperationType.CREATE_ITEM, "list", "a")
            queue.enqueue(SyncOperationType.CREATE_ITEM, "list", "b")

            queue.complete(listOf(queue.pending().first().id))

            assertFalse(queue.hasPendingForItem("a"))
            assertTrue(queue.hasPendingForItem("b"))
        }

    @Test
    fun `clearing a list removes only its operations`() =
        runTest {
            queue.enqueue(SyncOperationType.CREATE_ITEM, "list-1", "a")
            queue.enqueue(SyncOperationType.CREATE_ITEM, "list-2", "b")

            queue.clearList("list-1")

            assertEquals(listOf("list-2"), queue.pending().map { it.listLocalId })
        }
}
