package com.codecrafters.leaktrace.ui.server

import com.codecrafters.leaktrace.data.server.ServerHealth
import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.data.server.FeatureAvailability
import com.codecrafters.leaktrace.data.server.ServerReport
import com.codecrafters.leaktrace.data.server.ServerRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerViewModelTest {
    private class GatedRepository : ServerRepository {
        override val baseUrl = "https://leaktrace.test"
        var calls = 0
        var gate = CompletableDeferred<ServerReport>()
        override suspend fun check(): ServerReport {
            calls++
            return gate.await()
        }
    }

    private fun report(health: ServerHealth) = ServerReport("https://leaktrace.test", health, emptyMap(), 0L)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun checksOnStart_andPublishesTheReport() = runTest {
        val repository = GatedRepository()
        val viewModel = ServerViewModel(repository)

        assertTrue(viewModel.state.value.isChecking)
        assertNull(viewModel.state.value.report)

        repository.gate.complete(report(ServerHealth.Online("0.1.0")))

        assertFalse(viewModel.state.value.isChecking)
        assertEquals(ServerHealth.Online("0.1.0"), viewModel.state.value.report?.health)
    }

    @Test
    fun refresh_keepsLastReportWhileChecking_andIgnoresDuplicateRequests() = runTest {
        val repository = GatedRepository()
        val viewModel = ServerViewModel(repository)
        repository.gate.complete(report(ServerHealth.Unreachable))

        repository.gate = CompletableDeferred()
        viewModel.refresh()
        viewModel.refresh()

        assertEquals(2, repository.calls)
        assertTrue(viewModel.state.value.isChecking)
        assertEquals(ServerHealth.Unreachable, viewModel.state.value.report?.health)

        repository.gate.complete(report(ServerHealth.Online("0.1.0")))
        assertEquals(ServerHealth.Online("0.1.0"), viewModel.state.value.report?.health)
    }

    @Test
    fun refreshPublishesNewFeatureAvailabilityForScreens() = runTest {
        val repository = GatedRepository()
        val viewModel = ServerViewModel(repository)
        val first = report(ServerHealth.Online("1")).copy(
            features = mapOf(BackendFeature.Websites to FeatureAvailability.NotDeployed),
        )
        repository.gate.complete(first)
        assertEquals(FeatureAvailability.NotDeployed, viewModel.state.value.availability(BackendFeature.Websites))

        repository.gate = CompletableDeferred()
        viewModel.refresh()
        assertTrue(viewModel.state.value.isChecking)
        assertEquals(FeatureAvailability.NotDeployed, viewModel.state.value.availability(BackendFeature.Websites))

        repository.gate.complete(first.copy(features = mapOf(
            BackendFeature.Websites to FeatureAvailability.RequiresSignIn,
        )))
        assertFalse(viewModel.state.value.isChecking)
        assertEquals(FeatureAvailability.RequiresSignIn, viewModel.state.value.availability(BackendFeature.Websites))
    }
}
