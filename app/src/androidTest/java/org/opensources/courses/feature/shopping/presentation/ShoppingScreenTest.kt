package org.opensources.courses.feature.shopping.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.core.designsystem.theme.CoursesTheme
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
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
        composeRule.setContent { CoursesTheme { ShoppingScreen(state, query = "", actions = ShoppingActions()) } }

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
            CoursesTheme { ShoppingScreen(state, query = "", actions = ShoppingActions(onToggleItem = { toggled = it })) }
        }

        composeRule.onNodeWithText("Lait").performClick()

        assertEquals("Lait", toggled?.name)
    }

    @Test
    fun typingShowsLocalSuggestionsAndCustomOption() {
        var selected: ProductSuggestion? = null
        var customAdded = false
        val suggestions = listOf(ProductSuggestion("seed:lait", "Lait", "Produits laitiers"), ProductSuggestion("seed:lait-entier", "Lait entier", null))
        composeRule.setContent {
            var query by remember { mutableStateOf("") }
            CoursesTheme {
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
    fun emptyListInvitesToType() {
        composeRule.setContent {
            CoursesTheme { ShoppingScreen(ShoppingUiState(isLoading = false, listName = "BBQ"), query = "", actions = ShoppingActions()) }
        }

        composeRule.onNodeWithText("Votre liste est vide").assertIsDisplayed()
    }
}
