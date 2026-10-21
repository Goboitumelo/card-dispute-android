package com.example.disputeportal.ui

import com.example.disputeportal.data.DisputeRepository
import com.example.disputeportal.session.Session
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<DisputeRepository>()
    private val session = Session()

    @Before
    fun setUp() {
        every { repository.observeTransactions() } returns flowOf(emptyList())
        every { repository.observeDisputes() } returns flowOf(emptyList())
        coEvery { repository.clearCache() } just Runs
        coEvery { repository.refresh() } just Runs
        coEvery { repository.login(any(), any()) } returns "token-1"
        coEvery { repository.logout() } just Runs
    }

    private fun viewModel() = MainViewModel(session, repository)

    private fun httpError(status: Int): HttpException = mockk {
        every { code() } returns status
    }

    @Test
    fun refreshSucceedsWithoutError() = runTest {
        session.signIn("thandi@example.com", "token-1")
        coEvery { repository.refresh() } just Runs
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(SyncState(), viewModel.state.value)
        assertEquals("thandi@example.com", session.login.value?.email)
    }

    @Test
    fun refreshUnauthorizedSignsOutAndShowsSessionExpired() = runTest {
        session.signIn("thandi@example.com", "expired-token")
        coEvery { repository.refresh() } throws httpError(401)
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("Your session has expired. Sign in again.", viewModel.state.value.error)
        assertNull(session.login.value)
    }

    @Test
    fun refreshServerErrorShowsStatusCode() = runTest {
        coEvery { repository.refresh() } throws httpError(500)
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("The server returned an error (500).", viewModel.state.value.error)
    }

    @Test
    fun refreshNetworkFailureShowsReachabilityError() = runTest {
        coEvery { repository.refresh() } throws IOException("offline")
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(
            "Could not reach the server. Check that the API is running.",
            viewModel.state.value.error,
        )
    }

    @Test
    fun refreshTimeoutShowsTimeoutMessage() = runTest {
        coEvery { repository.refresh() } throws SocketTimeoutException("timeout")
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(
            "The request timed out. Check the connection and try again.",
            viewModel.state.value.error,
        )
    }

    @Test
    fun signInTimeoutShowsTimeoutMessageAndKeepsSessionEmpty() = runTest {
        coEvery { repository.login(any(), any()) } throws SocketTimeoutException("timeout")
        val viewModel = viewModel()

        viewModel.signIn("thandi@example.com", "password")
        advanceUntilIdle()

        assertEquals(
            "The request timed out. Check the connection and try again.",
            viewModel.state.value.error,
        )
        assertNull(session.login.value)
    }

    @Test
    fun signOutClearsStateAndCache() = runTest {
        session.signIn("thandi@example.com", "token-1")
        coEvery { repository.refresh() } throws IOException("offline")
        val viewModel = viewModel()
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(SyncState(), viewModel.state.value)
        assertNull(session.login.value)
        coVerify { repository.clearCache() }
    }

    @Test
    fun submitDisputeSuccessCallsOnDone() = runTest {
        coEvery { repository.submitDispute(any(), any(), any(), any()) } just Runs
        val viewModel = viewModel()
        var done = false

        viewModel.submitDispute("txn-1", "FRAUD", "Not mine", 1_000L) { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertNull(viewModel.action.value.error)
    }

    @Test
    fun submitDisputeConflictShowsMessageAndSkipsOnDone() = runTest {
        coEvery { repository.submitDispute(any(), any(), any(), any()) } throws httpError(409)
        val viewModel = viewModel()
        var done = false

        viewModel.submitDispute("txn-1", "FRAUD", "Not mine", 1_000L) { done = true }
        advanceUntilIdle()

        assertEquals(
            "You already have an open dispute for this transaction.",
            viewModel.action.value.error,
        )
        assertFalse(done)
    }

    @Test
    fun loadHistoryNotFoundShowsMessage() = runTest {
        coEvery { repository.history("dispute-1") } throws httpError(404)
        val viewModel = viewModel()

        viewModel.loadHistory("dispute-1")
        advanceUntilIdle()

        assertEquals("That dispute no longer exists.", viewModel.history.value.error)
    }

    @Test
    fun submitDisputeRefreshFailureIsShownInSyncStateAndKeepsSuccess() = runTest {
        coEvery { repository.submitDispute(any(), any(), any(), any()) } just Runs
        coEvery { repository.refresh() } throws IOException("offline")
        val viewModel = viewModel()
        var done = false

        viewModel.submitDispute("txn-1", "FRAUD", "Not mine", 1_000L) { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertNull(viewModel.action.value.error)
        assertEquals(
            "Could not reach the server. Check that the API is running.",
            viewModel.state.value.error,
        )
    }

    @Test
    fun refreshUnexpectedExceptionShowsGenericMessage() = runTest {
        coEvery { repository.refresh() } throws IllegalStateException("bad body")
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("An unexpected error occurred. Try again.", viewModel.state.value.error)
    }

    @Test
    fun submitDisputeUnexpectedExceptionShowsGenericMessageAndSkipsOnDone() = runTest {
        coEvery { repository.submitDispute(any(), any(), any(), any()) } throws IllegalStateException("bad body")
        val viewModel = viewModel()
        var done = false

        viewModel.submitDispute("txn-1", "FRAUD", "Not mine", 1_000L) { done = true }
        advanceUntilIdle()

        assertEquals("An unexpected error occurred. Try again.", viewModel.action.value.error)
        assertFalse(done)
    }

    @Test
    fun signInStoresTokenAndLoadsData() = runTest {
        val viewModel = viewModel()

        viewModel.signIn(" thandi@example.com ", "password")
        advanceUntilIdle()

        assertEquals("thandi@example.com", session.login.value?.email)
        assertEquals("token-1", session.login.value?.token)
        assertEquals(SyncState(), viewModel.state.value)
        coVerify { repository.login("thandi@example.com", "password") }
        coVerify { repository.clearCache() }
        coVerify { repository.refresh() }
    }

    @Test
    fun signInWrongPasswordShowsMessageAndKeepsSessionEmpty() = runTest {
        coEvery { repository.login(any(), any()) } throws httpError(401)
        val viewModel = viewModel()

        viewModel.signIn("thandi@example.com", "wrong-password")
        advanceUntilIdle()

        assertEquals("Email or password is incorrect.", viewModel.state.value.error)
        assertNull(session.login.value)
        coVerify(exactly = 0) { repository.refresh() }
    }

    @Test
    fun signOutRevokesTokenAndClearsSession() = runTest {
        session.signIn("thandi@example.com", "token-1")
        val viewModel = viewModel()

        viewModel.signOut()
        advanceUntilIdle()

        coVerify { repository.logout() }
        assertNull(session.login.value)
    }

    @Test
    fun signOutClearsSessionEvenWhenRevocationFails() = runTest {
        session.signIn("thandi@example.com", "token-1")
        coEvery { repository.logout() } throws IOException("offline")
        val viewModel = viewModel()

        viewModel.signOut()
        advanceUntilIdle()

        assertNull(session.login.value)
        coVerify { repository.clearCache() }
    }
}
