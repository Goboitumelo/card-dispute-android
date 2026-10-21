package com.example.disputeportal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shows [LoginScreen] when signed out, otherwise [SignedInScreen].
 *
 * @param viewModel the state holder for the app
 */
@Composable
fun DisputeApp(viewModel: MainViewModel) {
    val login by viewModel.login.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val disputes by viewModel.disputes.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    if (login == null) {
        LoginScreen(state = state, onSignIn = viewModel::signIn)
    } else {
        SignedInScreen(
            state = state,
            action = action,
            history = history,
            disputes = disputes,
            transactions = transactions,
            onRefresh = viewModel::refresh,
            onSignOut = viewModel::signOut,
            onSubmitDispute = viewModel::submitDispute,
            onWithdraw = viewModel::withdrawDispute,
            onClearAction = viewModel::clearAction,
            onLoadHistory = viewModel::loadHistory,
            onClearHistory = viewModel::clearHistory,
        )
    }
}

/**
 * Formats an amount in minor units for display, such as "ZAR 12.50".
 *
 * @param amountMinor the amount in minor units (cents)
 * @param currency the ISO currency code, or null to omit it
 * @return the formatted amount
 */
fun formatMoney(amountMinor: Long, currency: String?): String =
    listOfNotNull(currency, BigDecimal.valueOf(amountMinor, 2).toPlainString()).joinToString(" ")

/**
 * Formats an ISO-8601 instant in the device time zone, such as "3 Oct 2026".
 *
 * @param isoInstant the instant to format
 * @return the formatted date, or [isoInstant] unchanged if it cannot be parsed
 */
fun formatDate(isoInstant: String): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(isoInstant))
}.getOrDefault(isoInstant)

/**
 * Turns an enum name such as UNDER_REVIEW into "Under review".
 *
 * @param value the enum name
 * @return the readable label
 */
fun formatEnum(value: String): String =
    value.replace('_', ' ').lowercase(Locale.ENGLISH).replaceFirstChar { it.titlecase(Locale.ENGLISH) }
