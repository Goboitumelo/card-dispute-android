package com.example.disputeportal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.disputeportal.data.local.TransactionEntity
import java.math.BigDecimal

// Must match the backend DisputeReason enum names.
private val DISPUTE_REASONS = listOf(
    "UNAUTHORIZED_TRANSACTION",
    "DUPLICATE_CHARGE",
    "INCORRECT_AMOUNT",
    "PRODUCT_NOT_RECEIVED",
    "SERVICE_NOT_PROVIDED",
)

private const val MAX_DESCRIPTION = 1000

/**
 * Parses a decimal amount into minor units.
 *
 * @param text the amount, such as "12.50"
 * @return the amount in minor units (cents), or null if the input is invalid, not positive, or has more than two decimal places
 */
private fun parseMinorUnits(text: String): Long? {
    val amount = text.trim().toBigDecimalOrNull() ?: return null
    if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) return null
    return runCatching { amount.movePointRight(2).longValueExact() }.getOrNull()
}

/**
 * Form for submitting a dispute against a transaction.
 *
 * @param transaction the transaction being disputed
 * @param busy true while the submission is running
 * @param error the error message to show, if any
 * @param onBack called when the user leaves the form
 * @param onSubmit called with the reason, description and amount in minor units
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitDisputeScreen(
    transaction: TransactionEntity,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSubmit: (reason: String, description: String, amountMinor: Long) -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf(DISPUTE_REASONS.first()) }
    var description by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable {
        mutableStateOf(BigDecimal.valueOf(transaction.amountMinor, 2).toPlainString())
    }

    val amountMinor = parseMinorUnits(amountText)
    val amountError = when {
        amountMinor == null -> "Enter an amount such as 12.50."
        amountMinor > transaction.amountMinor -> "The amount cannot exceed the transaction amount."
        else -> null
    }
    val canSubmit = !busy && amountMinor != null && amountError == null && description.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New dispute") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(transaction.merchantName, style = MaterialTheme.typography.titleMedium)
            Text(
                formatMoney(transaction.amountMinor, transaction.currency),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))

            Text("Reason", style = MaterialTheme.typography.titleSmall)
            DISPUTE_REASONS.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option == reason,
                            role = Role.RadioButton,
                            onClick = { reason = option },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == reason, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(formatEnum(option))
                }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Disputed amount (${transaction.currency})") },
                singleLine = true,
                isError = amountError != null,
                supportingText = { if (amountError != null) Text(amountError) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= MAX_DESCRIPTION) description = it },
                label = { Text("What happened?") },
                minLines = 4,
                supportingText = { Text("${description.length}/$MAX_DESCRIPTION") },
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { amountMinor?.let { onSubmit(reason, description.trim(), it) } },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Submit dispute")
                }
            }
        }
    }
}
