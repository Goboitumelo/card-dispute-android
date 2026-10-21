@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.example.disputeportal.e2e

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignInFlowTest {

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
    fun validCredentials_signIn_loadsTransactionsWithBearerToken() {
        backend.transactions.add(SAMPLE_TRANSACTION)

        composeRule.showApp(app)
        composeRule.signIn()
        composeRule.waitUntilExactlyOneExists(hasText("Sign out"), WAIT_MS)

        composeRule.onNodeWithText("Transactions").performClick()
        composeRule.waitUntilExactlyOneExists(hasText("Coffee Corner"), WAIT_MS)

        composeRule.onNodeWithText("Disputes").performClick()
        composeRule.onNodeWithText("No disputes yet.").assertIsDisplayed()

        val login = backend.callsTo("POST", "/api/auth/login").single()
        assertTrue(login.body.contains(FakeBackend.EMAIL))
        val listing = backend.callsTo("GET", "/api/transactions").last()
        assertEquals("Bearer ${FakeBackend.TOKEN}", listing.authorization)
    }

    @Test
    fun wrongPassword_showsError_andStaysOnLogin() {
        composeRule.showApp(app)
        composeRule.signIn(password = "wrong-password")

        composeRule.waitUntilExactlyOneExists(hasText(MSG_WRONG_PASSWORD), WAIT_MS)
        composeRule.onNodeWithText("Sign in").assertIsDisplayed()
        composeRule.onAllNodesWithText("Sign out").assertCountEquals(0)
    }

    @Test
    fun invalidEmail_disablesSignIn_andSendsNoRequest() {
        composeRule.showApp(app)
        composeRule.onNodeWithText("Email").performTextInput("not-an-email")
        composeRule.onNodeWithText("Password").performTextInput(FakeBackend.PASSWORD)

        composeRule.onNodeWithText("Sign in").assertIsNotEnabled()
        assertTrue(backend.callsTo("POST", "/api/auth/login").isEmpty())
    }

    @Test
    fun serverUnreachable_showsNetworkError() {
        backend.close()

        composeRule.showApp(app)
        composeRule.signIn()

        composeRule.waitUntilExactlyOneExists(hasText(MSG_NETWORK), WAIT_MS)
    }

    @Test
    fun stalledServer_showsTimeoutMessage_notNetworkError() {
        backend.stallLogin = true

        composeRule.showApp(app)
        composeRule.signIn()

        composeRule.waitUntilExactlyOneExists(hasText(MSG_TIMEOUT), TIMEOUT_WAIT_MS)
    }

    @Test
    fun signOut_revokesToken_andReturnsToLogin() {
        composeRule.showAppAndSignIn(app)

        composeRule.onNodeWithText("Sign out").performClick()

        composeRule.waitUntilExactlyOneExists(hasText("Sign in"), WAIT_MS)
        assertEquals(1, backend.callsTo("POST", "/api/auth/logout").size)
    }
}
