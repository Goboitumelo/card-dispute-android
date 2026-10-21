package com.example.disputeportal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.disputeportal.data.DisputeRepository
import com.example.disputeportal.data.local.DisputeEntity
import com.example.disputeportal.data.local.TransactionEntity
import com.example.disputeportal.data.remote.HistoryDto
import com.example.disputeportal.session.Login
import com.example.disputeportal.session.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.cancellation.CancellationException

private const val NETWORK_ERROR = "Could not reach the server. Check that the API is running."
private const val TIMEOUT_ERROR = "The request timed out. Check the connection and try again."
private const val UNEXPECTED_ERROR = "An unexpected error occurred. Try again."

/** OkHttp reports timeouts as [InterruptedIOException], so they get their own message. */
private fun networkError(e: IOException): String =
    if (e is InterruptedIOException) TIMEOUT_ERROR else NETWORK_ERROR

data class DisputeRow(
    val dispute: DisputeEntity,
    val merchantName: String?,
    val currency: String?,
)

data class SyncState(
    val loading: Boolean = false,
    val error: String? = null,
)

data class ActionState(
    val busy: Boolean = false,
    val error: String? = null,
)

data class HistoryState(
    val loading: Boolean = false,
    val entries: List<HistoryDto> = emptyList(),
    val error: String? = null,
)

class MainViewModel(
    private val session: Session,
    private val repository: DisputeRepository,
) : ViewModel() {

    val login: StateFlow<Login?> = session.login

    val transactions: StateFlow<List<TransactionEntity>> = repository.observeTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val disputes: StateFlow<List<DisputeRow>> = combine(
        repository.observeDisputes(),
        repository.observeTransactions(),
    ) { disputeEntities, transactionEntities ->
        val byId = transactionEntities.associateBy { it.id }
        disputeEntities.map { dispute ->
            val transaction = byId[dispute.transactionId]
            DisputeRow(dispute, transaction?.merchantName, transaction?.currency)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _action = MutableStateFlow(ActionState())
    val action: StateFlow<ActionState> = _action.asStateFlow()

    private val _history = MutableStateFlow(HistoryState())
    val history: StateFlow<HistoryState> = _history.asStateFlow()

    /**
     * Exchanges the credentials for a bearer token, clears the cache, and loads data for the new user.
     *
     * @param email the email address, trimmed before use
     * @param password the user's password
     */
    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.value = SyncState(loading = true)
            val address = email.trim()
            val token = try {
                repository.login(address, password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = signInFailure(e)
                return@launch
            }
            repository.clearCache() // Never show the previous user's cached data.
            session.signIn(address, token)
            refresh()
        }
    }

    /**
     * Maps a failed sign-in request to the [SyncState] shown on the login screen.
     *
     * @param e the exception from the login request
     * @return the state to display
     */
    private fun signInFailure(e: Exception): SyncState = when {
        e is HttpException && e.code() == 401 -> SyncState(error = "Email or password is incorrect.")
        e is HttpException -> SyncState(error = "The server returned an error (${e.code()}).")
        e is IOException -> SyncState(error = networkError(e))
        else -> SyncState(error = UNEXPECTED_ERROR)
    }

    /**
     * Reloads transactions and disputes from the API and updates [state].
     *
     * A 401 response signs the user out.
     */
    fun refresh() {
        viewModelScope.launch {
            _state.value = SyncState(loading = true)
            _state.value = try {
                repository.refresh()
                SyncState()
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    session.signOut()
                    SyncState(error = "Your session has expired. Sign in again.")
                } else {
                    SyncState(error = "The server returned an error (${e.code()}).")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                SyncState(error = networkError(e))
            } catch (e: Exception) {
                SyncState(error = UNEXPECTED_ERROR)
            }
        }
    }

    /**
     * Revokes the token on the server, then signs out and resets the cached data and all screen state.
     *
     * Revocation is best-effort, so a failed call does not block sign-out. The token also expires on the server.
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                repository.logout()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Signing out locally must still work when the server is unreachable.
            }
            session.signOut()
            repository.clearCache()
            _state.value = SyncState()
            _action.value = ActionState()
            _history.value = HistoryState()
        }
    }

    /**
     * Submits a dispute and runs [onDone] if it succeeds.
     *
     * @param transactionId the transaction being disputed
     * @param reason one of the backend `DisputeReason` names
     * @param description the customer's explanation
     * @param amountMinor the disputed amount in minor units
     * @param onDone called after a successful submission
     */
    fun submitDispute(
        transactionId: String,
        reason: String,
        description: String,
        amountMinor: Long,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            runAction(
                conflictMessage = "You already have an open dispute for this transaction.",
                onSuccess = onDone,
            ) {
                repository.submitDispute(transactionId, reason, description, amountMinor)
            }
        }
    }

    /**
     * Withdraws an open dispute.
     *
     * @param disputeId the dispute to withdraw
     */
    fun withdrawDispute(disputeId: String) {
        viewModelScope.launch {
            runAction(
                conflictMessage = "This dispute can no longer be withdrawn.",
                onSuccess = {},
            ) {
                repository.withdrawDispute(disputeId)
            }
        }
    }

    /** Resets [action] to idle. */
    fun clearAction() {
        _action.value = ActionState()
    }

    /**
     * Loads the status history of a dispute into [history].
     *
     * @param disputeId the dispute to load
     */
    fun loadHistory(disputeId: String) {
        viewModelScope.launch {
            _history.value = HistoryState(loading = true)
            _history.value = try {
                HistoryState(entries = repository.history(disputeId))
            } catch (e: HttpException) {
                when (e.code()) {
                    401 -> {
                        session.signOut()
                        HistoryState(error = "Your session has expired. Sign in again.")
                    }
                    404 -> HistoryState(error = "That dispute no longer exists.")
                    else -> HistoryState(error = "The server returned an error (${e.code()}).")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                HistoryState(error = networkError(e))
            } catch (e: Exception) {
                HistoryState(error = UNEXPECTED_ERROR)
            }
        }
    }

    /** Resets [history] to its initial state. */
    fun clearHistory() {
        _history.value = HistoryState()
    }

    /**
     * Runs [block] while [action] is busy, then calls [onSuccess] and reloads [state].
     *
     * A failed reload is shown through [state], so it does not hide that the write succeeded.
     * Any failure of [block] is mapped to an error message in [action].
     *
     * @param conflictMessage message shown for a 409 response
     * @param onSuccess called after [block] succeeds
     * @param block the network call to run
     */
    private suspend fun runAction(
        conflictMessage: String,
        onSuccess: () -> Unit,
        block: suspend () -> Unit,
    ) {
        _action.value = ActionState(busy = true)
        try {
            block()
            _action.value = ActionState()
            onSuccess()
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _action.value = failure(e, conflictMessage)
        }
    }

    /**
     * Maps a failed request to the [ActionState] shown to the user.
     *
     * @param e the exception from the request
     * @param conflictMessage message shown for a 409 response
     * @return the action state to display
     */
    private fun failure(e: Throwable, conflictMessage: String): ActionState = when {
        e is HttpException && e.code() == 401 -> {
            session.signOut()
            ActionState(error = "Your session has expired. Sign in again.")
        }
        e is HttpException && e.code() == 404 ->
            ActionState(error = "That item no longer exists. Refresh and try again.")
        e is HttpException && e.code() == 409 -> ActionState(error = conflictMessage)
        e is HttpException && e.code() == 422 ->
            ActionState(error = "The dispute window has closed or the amount is too high.")
        e is HttpException -> ActionState(error = "The server returned an error (${e.code()}).")
        e is IOException -> ActionState(error = networkError(e))
        else -> ActionState(error = UNEXPECTED_ERROR)
    }
}
