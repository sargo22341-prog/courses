package org.opensources.courses.feature.lists.domain

import kotlinx.coroutines.flow.Flow

interface ShoppingListRepository {
    fun observeLists(): Flow<List<ShoppingList>>

    fun observeList(id: String): Flow<ShoppingList?>

    fun observeDefaultList(): Flow<ShoppingList?>

    suspend fun createList(name: String): ShoppingList

    suspend fun renameList(
        id: String,
        name: String,
    )

    /** Returns false when [id] is the last remaining list: the app always keeps one list. */
    suspend fun deleteList(id: String): Boolean

    suspend fun setDefaultList(id: String)

    /** Shows the lists in the order of [orderedIds], the order the user chose. */
    suspend fun reorderLists(orderedIds: List<String>)

    /** Returns the default list, creating one named [name] if none exists yet. */
    suspend fun ensureDefaultList(name: String): ShoppingList
}
