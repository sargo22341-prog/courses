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

    private companion object {
        val ITEM_OPERATIONS = setOf(CREATE_ITEM, UPDATE_ITEM, DELETE_ITEM, CHECK_ITEM, UNCHECK_ITEM)
    }
}
