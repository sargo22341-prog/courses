package org.opensources.courses.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.opensources.courses.core.network.ConnectivityObserver
import org.opensources.courses.core.sync.SyncOperationDao
import org.opensources.courses.core.sync.SyncOperationEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

val TestNow: Instant = Instant.parse("2026-09-13T10:00:00Z")

fun fixedClock(instant: Instant = TestNow): Clock = Clock.fixed(instant, ZoneOffset.UTC)

class FakeConnectivityObserver(
    online: Boolean,
) : ConnectivityObserver {
    override val isOnline: MutableStateFlow<Boolean> = MutableStateFlow(online)
}

/** In-memory equivalent of the Room DAO, with the same ordering and counting rules. */
class FakeSyncOperationDao : SyncOperationDao {
    private val rows = mutableListOf<SyncOperationEntity>()
    private var nextId = 1L
    private val count = MutableStateFlow(0)

    val all: List<SyncOperationEntity> get() = rows.sortedBy { it.id }

    override suspend fun insert(operation: SyncOperationEntity): Long {
        val stored = operation.copy(id = nextId++)
        rows += stored
        publish()
        return stored.id
    }

    override suspend fun getAll(): List<SyncOperationEntity> = all

    override fun observeCount(): Flow<Int> = count

    override suspend fun deleteByIds(ids: List<Long>) {
        rows.removeAll { it.id in ids }
        publish()
    }

    override suspend fun markFailed(
        ids: List<Long>,
        error: String,
    ) {
        rows.replaceAll { if (it.id in ids) it.copy(attemptCount = it.attemptCount + 1, lastError = error) else it }
    }

    override suspend fun countForItem(itemLocalId: String): Int = rows.count { it.itemLocalId == itemLocalId }

    override suspend fun deleteForList(listLocalId: String) {
        rows.removeAll { it.listLocalId == listLocalId }
        publish()
    }

    private fun publish() {
        count.value = rows.size
    }
}
