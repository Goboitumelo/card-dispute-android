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
     * Stores the credentials, clears the cache, and loads data for the new user.
     *
     * @param email the email address, trimmed before use
     * @param password the user's password
     */
    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            repository.clearCache() // Never show the previous user's cached data.
            session.signIn(email.trim(), password)
            refresh()
        }
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
                    SyncState(error = "Email or password is incorrect.")
                } else {
                    SyncState(error = "The server returned an error (${e.code()}).")
                }
            } catch (e: IOException) {
                SyncState(error = "Could not reach the server. Check that the API is running.")
            }
        }
    }

    /** Signs out and resets the cached data and all screen state. */
    fun signOut() {
        viewModelScope.launch {
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
            } catch (e: IOException) {
                HistoryState(error = "Could not reach the server. Check that the API is running.")
            }
        }
    }

    /** Resets [history] to its initial state. */
    fun clearHistory() {
        _history.value = HistoryState()
    }

    /**
     * Runs [block] while [action] is busy and maps any failure to an error message.
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
        } catch (e: HttpException) {
            _action.value = failure(e, conflictMessage)
        } catch (e: IOException) {
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
    private fun failure(e: Exception, conflictMessage: String): ActionState = when {
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
        else -> ActionState(error = "Could not reach the server. Check that the API is running.")
    }
}
