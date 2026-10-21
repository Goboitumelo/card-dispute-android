package com.example.disputeportal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DisputeApp(viewModel: MainViewModel) {
    val login by viewModel.login.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val disputes by viewModel.disputes.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    if (login == null) {
        LoginScreen(state = state, onSignIn = viewModel::signIn)
    } else {
        SignedInScreen(
            state = state,
            action = action,
            disputes = disputes,
            transactions = transactions,
            onRefresh = viewModel::refresh,
            onSignOut = viewModel::signOut,
            onSubmitDispute = viewModel::submitDispute,
            onWithdraw = viewModel::withdrawDispute,
            onClearAction = viewModel::clearAction,
        )
    }
}

fun formatMoney(amountMinor: Long, currency: String?): String =
    listOfNotNull(currency, BigDecimal.valueOf(amountMinor, 2).toPlainString()).joinToString(" ")

fun formatDate(isoInstant: String): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(isoInstant))
}.getOrDefault(isoInstant)

// Turns enum names such as UNDER_REVIEW into "Under review".
fun formatEnum(value: String): String =
    value.replace('_', ' ').lowercase(Locale.ENGLISH).replaceFirstChar { it.titlecase(Locale.ENGLISH) }
