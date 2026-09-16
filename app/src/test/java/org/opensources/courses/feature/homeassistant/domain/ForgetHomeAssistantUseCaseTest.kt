package org.opensources.courses.feature.homeassistant.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.testing.FakeHaConfigRepository
import org.opensources.courses.testing.FakeHaListLinkRepository
import org.opensources.courses.testing.FakeRemoteSyncEngine
import org.opensources.courses.testing.syncCoordinator

@OptIn(ExperimentalCoroutinesApi::class)
class ForgetHomeAssistantUseCaseTest {
    private val config = FakeHaConfigRepository()
    private val links = FakeHaListLinkRepository()
    private val engine = FakeRemoteSyncEngine()

    @Test
    fun `the connection is forgotten and every list unlinked`() =
        runTest {
            ForgetHomeAssistantUseCase(config, links, syncCoordinator(engine, backgroundScope))()

            assertTrue(config.forgotten)
            assertNull(config.credentials())
            assertTrue(links.unlinkedAll)
        }

    @Test
    fun `a running synchronisation ends before anything is forgotten`() =
        runTest {
            val coordinator = syncCoordinator(engine, backgroundScope)
            val forget = ForgetHomeAssistantUseCase(config, links, coordinator)
            engine.pause = CompletableDeferred()
            launch { coordinator.syncNow() }
            runCurrent()

            launch { forget() }
            runCurrent()
            assertFalse(links.unlinkedAll)

            engine.pause?.complete(Unit)
            advanceUntilIdle()
            assertTrue(links.unlinkedAll)
        }
}
