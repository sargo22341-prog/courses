package org.opensources.courses.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.opensources.courses.core.network.ConnectivityObserver
import org.opensources.courses.core.security.SecretStore
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

    /** Collectors currently observing the count. */
    val countObservers: Int get() = count.subscriptionCount.value

    /** Number of queries that read the queue, whatever their filter. */
    var reads = 0
        private set

    override suspend fun insert(operation: SyncOperationEntity): Long {
        val stored = operation.copy(id = nextId++)
        rows += stored
        publish()
        return stored.id
    }

    override suspend fun getAll(): List<SyncOperationEntity> {
        reads++
        return all
    }

    override fun observeCount(): Flow<Int> = count

    override suspend fun deleteByIds(ids: List<Long>) {
        rows.removeAll { it.id in ids }
        publish()
    }

    override suspend fun markFailed(ids: List<Long>) {
        rows.replaceAll { if (it.id in ids) it.copy(attemptCount = it.attemptCount + 1) else it }
    }

    override suspend fun countForItem(itemLocalId: String): Int {
        reads++
        return rows.count { it.itemLocalId == itemLocalId }
    }

    override suspend fun getItemIdsForList(listLocalId: String): List<String> {
        reads++
        return rows.filter { it.listLocalId == listLocalId }.mapNotNull { it.itemLocalId }.distinct()
    }

    override suspend fun deleteForList(listLocalId: String) {
        rows.removeAll { it.listLocalId == listLocalId }
        publish()
    }

    override suspend fun deleteAll() {
        rows.clear()
        publish()
    }

    private fun publish() {
        count.value = rows.size
    }
}

/** Secrets kept in memory, readable by the tests. */
class FakeSecretStore : SecretStore {
    val values = mutableMapOf<String, String>()

    override suspend fun read(name: String): String? = values[name]

    override suspend fun write(
        name: String,
        value: String,
    ) {
        values[name] = value
    }

    override suspend fun remove(name: String) {
        values.remove(name)
    }
}
