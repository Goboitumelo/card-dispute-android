package com.example.disputeportal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.disputeportal.data.DisputeRepository
import com.example.disputeportal.data.local.DisputeEntity
import com.example.disputeportal.data.local.TransactionEntity
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

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            repository.clearCache() // Never show the previous user's cached data.
            session.signIn(email.trim(), password)
            refresh()
        }
    }

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

    fun signOut() {
        viewModelScope.launch {
            session.signOut()
            repository.clearCache()
        }
    }

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

    fun clearAction() {
        _action.value = ActionState()
    }

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
