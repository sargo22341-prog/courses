package org.opensources.courses.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.feature.lists.domain.ShoppingList
import org.opensources.courses.feature.lists.domain.ShoppingListRepository

/** Local lists only, with the same default-list rules as Room. */
class FakeShoppingListRepository : ShoppingListRepository {
    val lists = MutableStateFlow<List<ShoppingList>>(emptyList())
    private var nextId = 1

    override fun observeLists(): Flow<List<ShoppingList>> = lists

    override fun observeList(id: String): Flow<ShoppingList?> = lists.map { all -> all.firstOrNull { it.id == id } }

    override fun observeDefaultList(): Flow<ShoppingList?> = lists.map { all -> all.firstOrNull { it.isDefault } }

    override suspend fun createList(name: String): ShoppingList {
        val created = ShoppingList("list${nextId++}", name, isDefault = lists.value.none { it.isDefault }, null, SyncStatus.LOCAL_ONLY)
        lists.value = lists.value + created
        return created
    }

    override suspend fun renameList(
        id: String,
        name: String,
    ) {
        lists.value = lists.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun deleteList(id: String): Boolean {
        if (lists.value.size <= 1) return false
        lists.value = lists.value.filterNot { it.id == id }
        return true
    }

    override suspend fun setDefaultList(id: String) {
        lists.value = lists.value.map { it.copy(isDefault = it.id == id) }
    }

    override suspend fun reorderLists(orderedIds: List<String>) {
        lists.value = lists.value.sortedBy { list -> orderedIds.indexOf(list.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
    }

    override suspend fun ensureDefaultList(name: String): ShoppingList = lists.value.firstOrNull { it.isDefault } ?: createList(name)
}
