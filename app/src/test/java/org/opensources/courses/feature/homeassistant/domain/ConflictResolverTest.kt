package org.opensources.courses.feature.homeassistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolverTest {
    private fun local(
        name: String = "Lait",
        checked: Boolean = false,
        quantity: Double = 1.0,
        pending: Boolean = false,
        deleted: Boolean = false,
    ) = LocalItemState(name, checked, quantity, null, pending, deleted)

    private fun remote(
        summary: String = "Lait",
        completed: Boolean = false,
        quantity: Double = 1.0,
    ) = RemoteItemState(summary, completed, quantity, null)

    @Test
    fun `identical states are in sync`() {
        assertEquals(ItemResolution.IN_SYNC, ConflictResolver.resolve(local(), remote(), compareQuantity = true))
    }

    @Test
    fun `remote change applies to a synced item (last write wins)`() {
        assertEquals(ItemResolution.APPLY_REMOTE, ConflictResolver.resolve(local(), remote(completed = true), compareQuantity = true))
    }

    @Test
    fun `pending local change is never overwritten`() {
        assertEquals(
            ItemResolution.KEEP_LOCAL,
            ConflictResolver.resolve(local(checked = true, pending = true), remote(summary = "Lait entier"), compareQuantity = true),
        )
    }

    @Test
    fun `remote deletion removes a synced item`() {
        assertEquals(ItemResolution.DELETE_LOCAL, ConflictResolver.resolve(local(), null, compareQuantity = true))
    }

    @Test
    fun `remote deletion of a locally modified item recreates it`() {
        assertEquals(ItemResolution.RECREATE_REMOTE, ConflictResolver.resolve(local(pending = true), null, compareQuantity = true))
    }

    @Test
    fun `local deletion waits until the remote item is gone`() {
        assertEquals(ItemResolution.KEEP_LOCAL, ConflictResolver.resolve(local(deleted = true, pending = true), remote(), compareQuantity = true))
        assertEquals(ItemResolution.DELETE_LOCAL, ConflictResolver.resolve(local(deleted = true), null, compareQuantity = true))
    }

    @Test
    fun `local deletion gives way to a remote modification`() {
        assertTrue(ConflictResolver.remoteChangedSince(local(deleted = true), remote(completed = true), compareQuantity = true))
        assertFalse(ConflictResolver.remoteChangedSince(local(deleted = true), remote(), compareQuantity = true))
    }

    @Test
    fun `local check state wins unless Home Assistant completed the item later`() {
        assertTrue(ConflictResolver.localStatusWins(1_000, remote(completed = false)))
        assertTrue(ConflictResolver.localStatusWins(1_000, RemoteItemState("Lait", true, 1.0, null, completedAt = 500)))
        assertFalse(ConflictResolver.localStatusWins(1_000, RemoteItemState("Lait", true, 1.0, null, completedAt = 2_000)))
    }

    @Test
    fun `quantity is ignored when the remote list cannot store it`() {
        assertEquals(ItemResolution.IN_SYNC, ConflictResolver.resolve(local(quantity = 3.0), remote(), compareQuantity = false))
        assertEquals(ItemResolution.APPLY_REMOTE, ConflictResolver.resolve(local(quantity = 3.0), remote(), compareQuantity = true))
    }
}
