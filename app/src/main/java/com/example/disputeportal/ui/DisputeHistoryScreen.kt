package com.example.disputeportal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.disputeportal.data.remote.HistoryDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisputeHistoryScreen(
    row: DisputeRow,
    state: HistoryState,
    onLoad: (String) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(row.dispute.id) { onLoad(row.dispute.id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dispute history") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(row.merchantName ?: "Unknown merchant", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatMoney(row.dispute.disputedAmountMinor, row.currency),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Status: ${formatEnum(row.dispute.status)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                state.error != null -> Text(
                    state.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                state.entries.isEmpty() -> Text(
                    "No status changes yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.entries) { entry -> HistoryEntryCard(entry) }
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(entry: HistoryDto) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(formatEnum(entry.toStatus), style = MaterialTheme.typography.titleSmall)
            entry.fromStatus?.let {
                Text("From ${formatEnum(it)}", style = MaterialTheme.typography.bodyMedium)
            }
            entry.note?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                formatDate(entry.changedAt),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
