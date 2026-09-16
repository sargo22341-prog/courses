package org.opensources.courses.feature.shopping.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.testing.FrenchCoursesTheme
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.SyncSnapshot
import org.opensources.courses.core.sync.SyncState
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.shopping.domain.ItemSection
import org.opensources.courses.feature.shopping.domain.ShoppingItem
import org.opensources.courses.feature.shopping.presentation.components.ADD_ITEM_FIELD_TAG

@RunWith(AndroidJUnit4::class)
class ShoppingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun item(
        name: String,
        checked: Boolean = false,
        quantity: Double = 1.0,
    ) = ShoppingItem("id-$name", "list", name, quantity, null, checked, null, 0, 0, SyncStatus.LOCAL_ONLY)

    private val state =
        ShoppingUiState(
            isLoading = false,
            listName = "Courses",
            toBuy = listOf(item("Lait", quantity = 2.0), item("Œufs", quantity = 12.0)),
            purchased = listOf(item("Pain", checked = true)),
        )

    @Test
    fun showsItemsToBuyAndPurchasedSeparately() {
        composeRule.setContent { FrenchCoursesTheme { ShoppingScreen(state, query = "", actions = ShoppingActions()) } }

        composeRule.onNodeWithText("Courses").assertIsDisplayed()
        composeRule.onNodeWithText("Lait").assertIsDisplayed()
        composeRule.onNodeWithText("12").assertIsDisplayed()
        composeRule.onNodeWithText("Achetés").assertIsDisplayed()
        composeRule.onNodeWithText("1 article acheté").assertIsDisplayed()
    }

    @Test
    fun tappingAnItemTogglesIt() {
        var toggled: ShoppingItem? = null
        composeRule.setContent {
            FrenchCoursesTheme { ShoppingScreen(state, query = "", actions = ShoppingActions(onToggleItem = { toggled = it })) }
        }

        composeRule.onNodeWithText("Lait").performClick()

        assertEquals("Lait", toggled?.name)
    }

    @Test
    fun deletingPurchasedItemsFromTheirHeaderAsksForConfirmation() {
        var deleted = false
        composeRule.setContent {
            FrenchCoursesTheme { ShoppingScreen(state, query = "", actions = ShoppingActions(onDeletePurchased = { deleted = true })) }
        }

        composeRule.onNodeWithContentDescription("Supprimer les articles achetés").performClick()
        composeRule.onNodeWithText("Supprimer les articles achetés ?").assertIsDisplayed()
        assertFalse(deleted)

        composeRule.onNodeWithText("Supprimer").performClick()

        assertTrue(deleted)
        composeRule.onNodeWithText("Supprimer les articles achetés ?").assertDoesNotExist()
    }

    @Test
    fun cancellingTheConfirmationKeepsPurchasedItems() {
        var deleted = false
        composeRule.setContent {
            FrenchCoursesTheme { ShoppingScreen(state, query = "", actions = ShoppingActions(onDeletePurchased = { deleted = true })) }
        }

        composeRule.onNodeWithContentDescription("Supprimer les articles achetés").performClick()
        composeRule.onNodeWithText("Annuler").performClick()

        assertFalse(deleted)
        composeRule.onNodeWithText("Pain").assertIsDisplayed()
    }

    @Test
    fun itemsToBuyAreShownUnderTheirCategoryWhenGrouped() {
        val grouped =
            state.copy(
                toBuySections =
                    listOf(
                        ItemSection(GroceryCategory.DAIRY_EGGS, listOf(item("Lait", quantity = 2.0))),
                        ItemSection(GroceryCategory.OTHER, listOf(item("Sauce maison"))),
                    ),
            )
        composeRule.setContent { FrenchCoursesTheme { ShoppingScreen(grouped, query = "", actions = ShoppingActions()) } }

        composeRule.onNodeWithText("Produits laitiers et œufs").assertIsDisplayed()
        composeRule.onNodeWithText("Lait").assertIsDisplayed()
        composeRule.onNodeWithText("Autres").assertIsDisplayed()
        composeRule.onNodeWithText("Sauce maison").assertIsDisplayed()
    }

    @Test
    fun typingShowsLocalSuggestionsAndCustomOption() {
        var selected: ProductSuggestion? = null
        var customAdded = false
        val suggestions = listOf(ProductSuggestion("seed:lait", "Lait", "Produits laitiers"), ProductSuggestion("seed:lait-entier", "Lait entier", null))
        composeRule.setContent {
            var query by remember { mutableStateOf("") }
            FrenchCoursesTheme {
                ShoppingScreen(
                    state = state.copy(suggestions = suggestions),
                    query = query,
                    actions =
                        ShoppingActions(
                            onQueryChange = { query = it },
                            onSuggestionSelected = { selected = it },
                            onAddCustomItem = { customAdded = true },
                        ),
                )
            }
        }

        composeRule.onNodeWithTag(ADD_ITEM_FIELD_TAG).performTextInput("lai")
        composeRule.onNodeWithText("Lait entier").assertIsDisplayed()
        composeRule.onNodeWithText("Ajouter « lai »").performClick()
        composeRule.onNodeWithText("Lait entier").performClick()

        assertTrue(customAdded)
        assertEquals("seed:lait-entier", selected?.productId)
    }

    @Test
    fun pullingDownRefreshesWhenHomeAssistantIsEnabled() {
        var refreshed = false
        val synced = state.copy(sync = SyncSnapshot(SyncState.ONLINE, remoteEnabled = true, pendingCount = 0, failure = null))
        composeRule.setContent {
            FrenchCoursesTheme { ShoppingScreen(synced, query = "", actions = ShoppingActions(onRefresh = { refreshed = true })) }
        }

        // A real pull: well beyond the refresh threshold, not just the height of one row.
        composeRule.onNodeWithText("Lait").performTouchInput { swipeDown(startY = top, endY = top + PULL_DISTANCE_PX, durationMillis = 400) }
        composeRule.waitForIdle()

        assertTrue(refreshed)
    }

    @Test
    fun emptyListInvitesToType() {
        composeRule.setContent {
            FrenchCoursesTheme { ShoppingScreen(ShoppingUiState(isLoading = false, listName = "BBQ"), query = "", actions = ShoppingActions()) }
        }

        composeRule.onNodeWithText("Votre liste est vide").assertIsDisplayed()
    }

    @Test
    fun swipingAnItemToTheLeftDeletesIt() {
        var deleted: ShoppingItem? = null
        composeRule.setContent {
            FrenchCoursesTheme { ShoppingScreen(state, query = "", actions = ShoppingActions(onDeleteItem = { deleted = it })) }
        }

        composeRule.onNodeWithText("Lait").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals("Lait", deleted?.name)
    }

    @Test
    fun aDeletedItemCanBeBroughtBack() {
        var undone = false
        var confirmed: ShoppingItem? = null
        val milk = item("Lait", quantity = 2.0)
        val deleting = state.copy(toBuy = state.toBuy - milk, pendingDeletion = milk)
        composeRule.setContent {
            FrenchCoursesTheme {
                ShoppingScreen(deleting, query = "", actions = ShoppingActions(onUndoDeletion = { undone = true }, onDeletionConfirmed = { confirmed = it }))
            }
        }

        composeRule.onNodeWithText("« Lait » supprimé").assertIsDisplayed()
        composeRule.onNodeWithText("Annuler").performClick()
        composeRule.waitForIdle()

        assertTrue(undone)
        assertEquals(null, confirmed)
    }

    @Test
    fun theDeletionIsConfirmedOnceTheUndoOfferEnds() {
        var confirmed: ShoppingItem? = null
        val milk = item("Lait", quantity = 2.0)
        val deleting = state.copy(toBuy = state.toBuy - milk, pendingDeletion = milk)
        composeRule.setContent {
            FrenchCoursesTheme { ShoppingScreen(deleting, query = "", actions = ShoppingActions(onDeletionConfirmed = { confirmed = it })) }
        }

        composeRule.mainClock.advanceTimeBy(UNDO_OFFER_ELAPSED_MILLIS)
        composeRule.waitForIdle()

        assertEquals(milk, confirmed)
    }

    @Test
    fun swipingThenUndoingBringsTheItemBack() {
        var screen by mutableStateOf(state)
        var deletions = 0
        composeRule.setContent {
            FrenchCoursesTheme {
                ShoppingScreen(
                    screen,
                    query = "",
                    actions =
                        ShoppingActions(
                            onDeleteItem = { deleted ->
                                deletions++
                                screen = screen.copy(toBuy = screen.toBuy - deleted, pendingDeletion = deleted)
                            },
                            onUndoDeletion = { screen = state },
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Lait").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Annuler").performClick()
        composeRule.waitForIdle()

        // Shown again, and not deleted a second time by the swipe it was removed with.
        composeRule.onNodeWithText("Lait").assertIsDisplayed()
        assertEquals(1, deletions)
    }

    private companion object {
        const val PULL_DISTANCE_PX = 1_200f

        /** Beyond the short snackbar duration, whatever the accessibility settings of the test device. */
        const val UNDO_OFFER_ELAPSED_MILLIS = 60_000L
    }
}
