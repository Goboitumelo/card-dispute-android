@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.example.disputeportal.e2e

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.disputeportal.data.remote.HistoryDto
import com.example.disputeportal.data.remote.SubmitDisputeRequestDto
import com.google.gson.Gson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisputeFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val backend = FakeBackend()
    private lateinit var app: TestApp

    @Before
    fun setUp() {
        app = TestApp(InstrumentationRegistry.getInstrumentation().targetContext, backend.baseUrl)
    }

    @After
    fun tearDown() {
        app.close()
        backend.close()
    }

    @Test
    fun transactionsTab_opensDisputeForm_forTheTransaction() {
        backend.transactions.add(SAMPLE_TRANSACTION)
        composeRule.showAppAndSignIn(app)

        openDisputeFormForSampleTransaction()

        composeRule.onNodeWithText("New dispute").assertIsDisplayed()
        composeRule.onNodeWithText("Submit dispute").assertIsNotEnabled()
    }

    @Test
    fun submitDispute_postsReasonAndAmount_andShowsDisputeOpen() {
        backend.transactions.add(SAMPLE_TRANSACTION)
        composeRule.showAppAndSignIn(app)
        openDisputeFormForSampleTransaction()

        composeRule.onNodeWithText("Duplicate charge").performClick()
        composeRule.onNodeWithText("What happened?").performTextInput("Charged twice at checkout")
        composeRule.onNodeWithText("Submit dispute").performClick()

        composeRule.waitUntilExactlyOneExists(hasText("Dispute open"), WAIT_MS)
        val submit = backend.callsTo("POST", "/api/disputes").single()
        assertEquals("Bearer ${FakeBackend.TOKEN}", submit.authorization)
        assertEquals(
            SubmitDisputeRequestDto(
                transactionId = "tx-1",
                reason = "DUPLICATE_CHARGE",
                description = "Charged twice at checkout",
                disputedAmountMinor = 3750,
            ),
            Gson().fromJson(submit.body, SubmitDisputeRequestDto::class.java),
        )
    }

    @Test
    fun submitDispute_whenOpenDisputeExists_showsConflictMessage() {
        backend.transactions.add(SAMPLE_TRANSACTION)
        backend.rejectSubmitsWithConflict = true
        composeRule.showAppAndSignIn(app)
        openDisputeFormForSampleTransaction()

        composeRule.onNodeWithText("What happened?").performTextInput("Charged twice at checkout")
        composeRule.onNodeWithText("Submit dispute").performClick()

        composeRule.waitUntilExactlyOneExists(hasText(MSG_CONFLICT), WAIT_MS)
        composeRule.onNodeWithText("New dispute").assertIsDisplayed()
    }

    @Test
    fun withdrawDispute_afterConfirmation_marksDisputeWithdrawn() {
        backend.transactions.add(SAMPLE_TRANSACTION)
        backend.disputes.add(SAMPLE_DISPUTE)
        composeRule.showAppAndSignIn(app)

        composeRule.waitUntilExactlyOneExists(hasText("Status: Submitted"), WAIT_MS)
        // The row is a clickable card, so the button is only reachable in the unmerged tree.
        composeRule.onNodeWithText("Withdraw", useUnmergedTree = true).performClick()
        composeRule.waitUntilExactlyOneExists(hasText("Withdraw dispute?"), WAIT_MS)
        composeRule.onAllNodes(hasText("Withdraw") and hasAnyAncestor(isDialog())).onFirst().performClick()

        composeRule.waitUntilExactlyOneExists(hasText("Status: Withdrawn"), WAIT_MS)
        composeRule.onAllNodesWithText("Withdraw").assertCountEquals(0)
        assertEquals(1, backend.callsTo("POST", "/api/disputes/dispute-1/withdraw").size)
    }

    @Test
    fun disputeHistory_showsStatusChanges() {
        backend.transactions.add(SAMPLE_TRANSACTION)
        backend.disputes.add(SAMPLE_DISPUTE)
        backend.histories["dispute-1"] = listOf(
            HistoryDto(fromStatus = null, toStatus = "SUBMITTED", note = null, changedAt = CREATED_AT),
            HistoryDto(
                fromStatus = "SUBMITTED",
                toStatus = "UNDER_REVIEW",
                note = "Assigned to an analyst",
                changedAt = CREATED_AT,
            ),
        )
        composeRule.showAppAndSignIn(app)

        composeRule.waitUntilExactlyOneExists(hasText("Coffee Corner"), WAIT_MS)
        composeRule.onNodeWithText("Coffee Corner").performClick()

        composeRule.waitUntilExactlyOneExists(hasText("Dispute history"), WAIT_MS)
        composeRule.waitUntilExactlyOneExists(hasText("Assigned to an analyst"), WAIT_MS)
        composeRule.onNodeWithText("Under review").assertIsDisplayed()
        composeRule.onNodeWithText("From Submitted").assertIsDisplayed()
    }

    /** Opens the dispute form for [SAMPLE_TRANSACTION] from the Transactions tab. */
    private fun openDisputeFormForSampleTransaction() {
        composeRule.onNodeWithText("Transactions").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("Coffee Corner"), WAIT_MS)
        composeRule.onNodeWithText("Dispute").performClick()
    }
}
