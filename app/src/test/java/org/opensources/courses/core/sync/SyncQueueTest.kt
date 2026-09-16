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

            queue.fail(listOf(id))
            queue.fail(listOf(id))

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
    fun `the last attempt is the one that reaches the limit`() =
        runTest {
            queue.enqueue(SyncOperationType.CREATE_ITEM, "list", "a")
            val id = queue.pending().single().id

            repeat(SyncQueue.MAX_ATTEMPTS - 2) { queue.fail(listOf(id)) }
            assertFalse(queue.pending().single().isLastAttempt)

            queue.fail(listOf(id))
            assertTrue(queue.pending().single().isLastAttempt)
        }

    @Test
    fun `pending items are listed per list, without list operations`() =
        runTest {
            queue.enqueue(SyncOperationType.CREATE_LIST, "list-1")
            queue.enqueue(SyncOperationType.CREATE_ITEM, "list-1", "a")
            queue.enqueue(SyncOperationType.CHECK_ITEM, "list-1", "a")
            queue.enqueue(SyncOperationType.CREATE_ITEM, "list-2", "b")

            assertEquals(setOf("a"), queue.pendingItemIds("list-1"))
        }

    @Test
    fun `clearing the queue removes every operation`() =
        runTest {
            queue.enqueue(SyncOperationType.DELETE_LIST, "gone", remoteListId = "todo.bbq")
            queue.enqueue(SyncOperationType.CREATE_ITEM, "list", "a")

            queue.clear()

            assertTrue(queue.pending().isEmpty())
            assertEquals(0, queue.observePendingCount().first())
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
