package org.opensources.courses.feature.lists.presentation.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.opensources.courses.R
import org.opensources.courses.feature.lists.domain.ShoppingList

/**
 * Drag and drop of the lists, without a library: the dragged row follows the finger over the others,
 * which move aside ([androidx.compose.foundation.lazy.LazyItemScope.animateItem]) as soon as its middle
 * passes over them. The order is kept here while dragging and handed over once, when the finger lifts:
 * the database is written once per move, not at each row passed.
 */
@Stable
internal class ListDragState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
) {
    /** The lists in the order shown: the stored order, or the one being dragged. */
    var order by mutableStateOf<List<ShoppingList>>(emptyList())
        private set

    var draggedId by mutableStateOf<String?>(null)
        private set

    private var startFingerY = 0f
    private var startOffset = 0
    private var fingerDelta by mutableFloatStateOf(0f)

    /** Follows the stored order, except while a row is held: the finger decides until it lifts. */
    fun sync(lists: List<ShoppingList>) {
        if (draggedId == null) order = lists
    }

    /** [fingerY] in root coordinates: the handle moves with its row, its own coordinates would too. */
    fun start(
        id: String,
        fingerY: Float,
    ) {
        val row = rowOf(id) ?: return
        draggedId = id
        startFingerY = fingerY
        startOffset = row.offset
        fingerDelta = 0f
    }

    /** Returns true when the dragged row took the place of another one. */
    fun dragTo(fingerY: Float): Boolean {
        fingerDelta = fingerY - startFingerY
        val dragged = draggedId?.let(::rowOf) ?: return false
        val middle = (startOffset + fingerDelta + dragged.size / 2f).toInt()
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key != dragged.key && middle in it.offset..(it.offset + it.size) } ?: return false
        val from = order.indexOfFirst { it.id == dragged.key }
        val to = order.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return false
        val first = listState.firstVisibleItemIndex
        if (from == first || to == first) {
            // A lazy list keeps its first row in place: without this, it would scroll along with it.
            scope.launch { listState.scrollToItem(first, listState.firstVisibleItemScrollOffset) }
        }
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        return true
    }

    /** How far the dragged row is drawn from where the list lays it out; 0 for the others. */
    fun offsetOf(id: String): Float {
        if (id != draggedId) return 0f
        val row = rowOf(id) ?: return 0f
        return startOffset + fingerDelta - row.offset
    }

    /** The order to save, or null when no row was held. */
    fun end(): List<String>? {
        if (draggedId == null) return null
        draggedId = null
        fingerDelta = 0f
        return order.map { it.id }
    }

    private fun rowOf(id: String): LazyListItemInfo? = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
}

@Composable
internal fun rememberListDragState(listState: LazyListState): ListDragState {
    val scope = rememberCoroutineScope()
    return remember(listState) { ListDragState(listState, scope) }
}

/**
 * The handle that drags the row of [listId]. A tick is felt when the row is picked up and each time
 * it passes another one; [onDrop] receives the new order. Screen readers move lists from the row menu.
 */
@Composable
internal fun ListDragHandle(
    state: ListDragState,
    listId: String,
    onDrop: (List<String>) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Icon(
        painter = painterResource(R.drawable.ic_drag_handle),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .size(48.dp)
                .onGloballyPositioned { coordinates = it }
                .pointerInput(state, listId) {
                    fun rootY(localY: Float) = coordinates?.localToRoot(Offset(0f, localY))?.y
                    detectDragGestures(
                        onDragStart = { offset ->
                            rootY(offset.y)?.let { state.start(listId, it) }
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (rootY(change.position.y)?.let(state::dragTo) == true) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                        },
                        onDragEnd = {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            state.end()?.let(onDrop)
                        },
                        onDragCancel = { state.end()?.let(onDrop) },
                    )
                }.wrapContentSize(),
    )
}
