package org.opensources.courses.feature.homeassistant.domain

import org.opensources.courses.core.sync.SyncFailure

/** Every call throws [HomeAssistantException] on failure. */
interface HomeAssistantGateway {
    suspend fun testConnection(credentials: HaCredentials)

    suspend fun getTodoLists(credentials: HaCredentials): List<HaTodoList>

    suspend fun getItems(
        credentials: HaCredentials,
        entityId: String,
    ): List<HaTodoItem>

    suspend fun addItem(
        credentials: HaCredentials,
        entityId: String,
        summary: String,
        description: String?,
    )

    /**
     * Only the given fields change: a null [summary] or [completed] is left as it is in Home
     * Assistant. [description] is sent only when [sendDescription] is true (null clears it).
     */
    suspend fun updateItem(
        credentials: HaCredentials,
        entityId: String,
        uid: String,
        summary: String?,
        completed: Boolean?,
        description: String?,
        sendDescription: Boolean,
    )

    suspend fun removeItem(
        credentials: HaCredentials,
        entityId: String,
        uid: String,
    )

    /**
     * Creates a Local To-do list. Requires an administrator token: a valid token without these
     * rights is [HaErrorKind.REJECTED].
     */
    suspend fun createList(
        credentials: HaCredentials,
        name: String,
    ): HaCreatedList

    /** Deletes a list created by the app; same rights as [createList]. */
    suspend fun deleteList(
        credentials: HaCredentials,
        configEntryId: String,
    )
}

enum class HaErrorKind {
    INVALID_URL,
    UNREACHABLE,
    UNAUTHORIZED,
    NOT_FOUND,
    REJECTED,
    PROTOCOL,
    ;

    fun toSyncFailure(): SyncFailure =
        when (this) {
            UNREACHABLE, INVALID_URL -> SyncFailure.UNREACHABLE
            UNAUTHORIZED -> SyncFailure.UNAUTHORIZED
            NOT_FOUND, REJECTED, PROTOCOL -> SyncFailure.PROTOCOL
        }
}

class HomeAssistantException(
    val kind: HaErrorKind,
    cause: Throwable? = null,
) : Exception(kind.name, cause) {
    /** The whole synchronisation must stop: no other request can succeed either. */
    val isFatal: Boolean get() = kind == HaErrorKind.UNREACHABLE || kind == HaErrorKind.UNAUTHORIZED || kind == HaErrorKind.INVALID_URL
}
