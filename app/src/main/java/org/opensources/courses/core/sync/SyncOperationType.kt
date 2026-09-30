package org.opensources.courses.core.sync

enum class SyncOperationType {
    CREATE_ITEM,
    UPDATE_ITEM,
    DELETE_ITEM,
    CHECK_ITEM,
    UNCHECK_ITEM,
    CREATE_LIST,
    DELETE_LIST,
    UPDATE_LIST,
    ;

    val isItemOperation: Boolean
        get() = this in ITEM_OPERATIONS

    /**
     * Operations of the same item (or list) made useless by this one. Only the current state is ever
     * sent, and the date of the latest check or uncheck: a hundred taps on "+" stay one operation.
     */
    val supersedes: Set<SyncOperationType>
        get() =
            when (this) {
                UPDATE_ITEM -> setOf(UPDATE_ITEM)
                CHECK_ITEM, UNCHECK_ITEM -> STATUS_CHANGES
                UPDATE_LIST -> setOf(UPDATE_LIST)
                CREATE_ITEM, DELETE_ITEM, CREATE_LIST, DELETE_LIST -> emptySet()
            }

    private companion object {
        val ITEM_OPERATIONS = setOf(CREATE_ITEM, UPDATE_ITEM, DELETE_ITEM, CHECK_ITEM, UNCHECK_ITEM)
        val STATUS_CHANGES = setOf(CHECK_ITEM, UNCHECK_ITEM)
    }
}
