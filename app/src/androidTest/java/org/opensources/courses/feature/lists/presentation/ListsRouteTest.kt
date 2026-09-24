package org.opensources.courses.feature.lists.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.RemoteListChoice
import org.opensources.courses.testing.FrenchCoursesTheme
import org.opensources.courses.testing.TestRemoteListImport
import org.opensources.courses.testing.TestRepositories

@RunWith(AndroidJUnit4::class)
class ListsRouteTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val repositories = TestRepositories.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)

    @After
    fun close() {
        repositories.database.close()
    }

    @Test
    fun aCreatedListIsOpenedOnce() {
        val opened = mutableListOf<String>()
        val viewModel = ListsViewModel(repositories.lists, TestRemoteListImport(repositories.links, available = false))
        composeRule.setContent {
            FrenchCoursesTheme { ListsRoute(onBack = {}, onOpenList = { opened += it }, viewModel = viewModel) }
        }

        // What TalkBack reads: the button is labelled.
        composeRule.onNodeWithContentDescription("Nouvelle liste").performClick()
        composeRule.onNodeWithText("Nom de la liste").performTextInput("BBQ")
        composeRule.onNodeWithText("Créer").performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) { opened.isNotEmpty() }
        composeRule.waitForIdle()

        val created = runBlocking { repositories.database.shoppingListDao().getAll() }.single { it.name == "BBQ" }
        assertEquals(listOf(created.localId), opened)
        assertNull(viewModel.createdListId.value)
    }

    @Test
    fun aListMovedUpFromItsMenuKeepsItsNewPlace() {
        val viewModel = ListsViewModel(repositories.lists, TestRemoteListImport(repositories.links, available = false))
        runBlocking {
            repositories.lists.createList("Courses")
            repositories.lists.createList("BBQ")
        }
        composeRule.setContent {
            FrenchCoursesTheme { ListsRoute(onBack = {}, onOpenList = {}, viewModel = viewModel) }
        }

        composeRule.onNodeWithContentDescription("Actions pour BBQ").performClick()
        // The first list cannot go higher: only the second one offers it.
        composeRule.onNodeWithText("Monter").performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            runBlocking { repositories.lists.observeLists().first() }.first().name == "BBQ"
        }

        composeRule.onNodeWithContentDescription("Actions pour BBQ").performClick()
        composeRule.onNodeWithText("Monter").assertDoesNotExist()
        composeRule.onNodeWithText("Descendre").assertIsDisplayed()
    }

    @Test
    fun aHomeAssistantListImportedFromTheNewListDialogIsLinkedAndOpened() {
        val opened = mutableListOf<String>()
        val remote = TestRemoteListImport(repositories.links, available = true, lists = listOf(RemoteListChoice(MEALIE_ENTITY, "Mealie")))
        val viewModel = ListsViewModel(repositories.lists, remote)
        composeRule.setContent {
            FrenchCoursesTheme { ListsRoute(onBack = {}, onOpenList = { opened += it }, viewModel = viewModel) }
        }

        composeRule.onNodeWithContentDescription("Nouvelle liste").performClick()
        composeRule.onNodeWithText("Importer une liste de Home Assistant").performClick()
        composeRule.onNodeWithText("Mealie").performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) { opened.isNotEmpty() }
        composeRule.waitForIdle()

        val imported = runBlocking { repositories.database.shoppingListDao().getAll() }.single()
        assertEquals("Mealie", imported.name)
        assertEquals(MEALIE_ENTITY, imported.remoteId)
        assertEquals(SyncStatus.SYNCED, imported.syncStatus)
        // Not an "all lists" import: leaving that mode would remove it.
        assertFalse(imported.importedFromRemote)
        assertEquals(listOf(imported.localId), opened)
    }

    @Test
    fun withoutHomeAssistantTheNewListDialogOffersNoImport() {
        val viewModel = ListsViewModel(repositories.lists, TestRemoteListImport(repositories.links, available = false))
        composeRule.setContent {
            FrenchCoursesTheme { ListsRoute(onBack = {}, onOpenList = {}, viewModel = viewModel) }
        }

        composeRule.onNodeWithContentDescription("Nouvelle liste").performClick()
        composeRule.onNodeWithText("Nom de la liste").assertIsDisplayed()
        composeRule.onNodeWithText("Importer une liste de Home Assistant").assertDoesNotExist()
    }

    private companion object {
        const val MEALIE_ENTITY = "todo.mealie_courses"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
