package com.example.disputeportal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.disputeportal.data.local.TransactionEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignedInScreen(
    state: SyncState,
    action: ActionState,
    disputes: List<DisputeRow>,
    transactions: List<TransactionEntity>,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onSubmitDispute: (transactionId: String, reason: String, description: String, amountMinor: Long, onDone: () -> Unit) -> Unit,
    onWithdraw: (disputeId: String) -> Unit,
    onClearAction: () -> Unit,
    history: HistoryState,
    onLoadHistory: (disputeId: String) -> Unit,
    onClearHistory: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var draftTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var withdrawTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var historyDisputeId by rememberSaveable { mutableStateOf<String?>(null) }

    val draft = transactions.firstOrNull { it.id == draftTransactionId }
    if (draft != null) {
        SubmitDisputeScreen(
            transaction = draft,
            busy = action.busy,
            error = action.error,
            onBack = {
                draftTransactionId = null
                onClearAction()
            },
            onSubmit = { reason, description, amountMinor ->
                onSubmitDispute(draft.id, reason, description, amountMinor) { draftTransactionId = null }
            },
        )
        return
    }

    val historyRow = disputes.firstOrNull { it.dispute.id == historyDisputeId }
    if (historyRow != null) {
        DisputeHistoryScreen(
            row = historyRow,
            state = history,
            onLoad = onLoadHistory,
            onBack = {
                historyDisputeId = null
                onClearHistory()
            },
        )
        return
    }

    val openTransactionIds = disputes
        .filter { it.dispute.status in OPEN_STATUSES }
        .map { it.dispute.transactionId }
        .toSet()

    withdrawTargetId?.let { disputeId ->
        AlertDialog(
            onDismissRequest = { withdrawTargetId = null },
            title = { Text("Withdraw dispute?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onWithdraw(disputeId)
                    withdrawTargetId = null
                }) { Text("Withdraw") }
            },
            dismissButton = {
                TextButton(onClick = { withdrawTargetId = null }) { Text("Keep dispute") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Card Dispute Portal") },
                actions = {
                    TextButton(onClick = onRefresh, enabled = !state.loading) { Text("Refresh") }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Disputes") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Transactions") })
            }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            listOfNotNull(state.error, action.error).forEach {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            if (tab == 0) {
                DisputesList(
                    rows = disputes,
                    busy = action.busy,
                    onWithdrawRequest = { withdrawTargetId = it },
                    onOpenHistory = { historyDisputeId = it },
                )
            } else {
                TransactionsList(
                    transactions = transactions,
                    openTransactionIds = openTransactionIds,
                    onDispute = {
                        onClearAction()
                        draftTransactionId = it
                    },
                )
            }
        }
    }
}

@Composable
private fun DisputesList(
    rows: List<DisputeRow>,
    busy: Boolean,
    onWithdrawRequest: (String) -> Unit,
    onOpenHistory: (String) -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyMessage("No disputes yet.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(rows, key = { it.dispute.id }) { row ->
            Card(onClick = { onOpenHistory(row.dispute.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(row.merchantName ?: "Unknown merchant", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatMoney(row.dispute.disputedAmountMinor, row.currency),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(formatEnum(row.dispute.reason), style = MaterialTheme.typography.bodyMedium)
                    Text("Status: ${formatEnum(row.dispute.status)}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Opened ${formatDate(row.dispute.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (row.dispute.status in OPEN_STATUSES) {
                        TextButton(
                            onClick = { onWithdrawRequest(row.dispute.id) },
                            enabled = !busy,
                        ) { Text("Withdraw") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionsList(
    transactions: List<TransactionEntity>,
    openTransactionIds: Set<String>,
    onDispute: (String) -> Unit,
) {
    if (transactions.isEmpty()) {
        EmptyMessage("No transactions found.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(transactions, key = { it.id }) { transaction ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(transaction.merchantName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatMoney(transaction.amountMinor, transaction.currency),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Card ending ${transaction.cardLastFour}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        formatDate(transaction.occurredAt),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Button(
                        onClick = { onDispute(transaction.id) },
                        enabled = transaction.id !in openTransactionIds,
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text(if (transaction.id in openTransactionIds) "Dispute open" else "Dispute") }
                }
            }
        }
    }
}

// Mirrors DisputeStatus.OPEN on the backend.
private val OPEN_STATUSES = setOf("SUBMITTED", "UNDER_REVIEW", "INFO_REQUESTED")

@Composable
private fun EmptyMessage(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
}
