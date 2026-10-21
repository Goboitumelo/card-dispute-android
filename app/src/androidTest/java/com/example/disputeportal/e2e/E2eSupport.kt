@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.example.disputeportal.e2e

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.room.Room
import com.example.disputeportal.data.DisputeRepository
import com.example.disputeportal.data.local.AppDatabase
import com.example.disputeportal.data.remote.DisputeDto
import com.example.disputeportal.data.remote.HistoryDto
import com.example.disputeportal.data.remote.LoginRequestDto
import com.example.disputeportal.data.remote.LoginResponseDto
import com.example.disputeportal.data.remote.SubmitDisputeRequestDto
import com.example.disputeportal.data.remote.TransactionDto
import com.example.disputeportal.data.remote.buildApi
import com.example.disputeportal.session.Session
import com.example.disputeportal.ui.DisputeApp
import com.example.disputeportal.ui.MainViewModel
import com.example.disputeportal.ui.theme.DisputeTheme
import com.google.gson.Gson
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

const val WAIT_MS = 10_000L
const val TIMEOUT_WAIT_MS = 30_000L

const val MSG_NETWORK = "Could not reach the server. Check that the API is running."
const val MSG_TIMEOUT = "The request timed out. Check the connection and try again."
const val MSG_WRONG_PASSWORD = "Email or password is incorrect."
const val MSG_CONFLICT = "You already have an open dispute for this transaction."

const val CREATED_AT = "2026-10-03T10:00:00Z"

val SAMPLE_TRANSACTION = TransactionDto(
    id = "tx-1",
    cardLastFour = "4242",
    merchantName = "Coffee Corner",
    amountMinor = 3750,
    currency = "ZAR",
    occurredAt = "2026-10-02T08:30:00Z",
)

val SAMPLE_DISPUTE = DisputeDto(
    id = "dispute-1",
    transactionId = "tx-1",
    reason = "UNAUTHORIZED_TRANSACTION",
    description = "I did not make this purchase",
    disputedAmountMinor = 3750,
    status = "SUBMITTED",
    createdAt = CREATED_AT,
    updatedAt = CREATED_AT,
)

/** One request received by [FakeBackend]. */
data class ApiCall(
    val method: String,
    val path: String,
    val authorization: String?,
    val body: String,
)

/**
 * A local stand-in for the Spring Boot API, served by MockWebServer.
 *
 * The app runs its real Retrofit, OkHttp, and Room stack against it. Tests seed [transactions],
 * [disputes], and [histories] and then read [calls] to check what the app sent.
 */
class FakeBackend : AutoCloseable {
    private val gson = Gson()
    private val server = MockWebServer()
    private var closed = false

    val calls = CopyOnWriteArrayList<ApiCall>()
    val transactions = CopyOnWriteArrayList<TransactionDto>()
    val disputes = CopyOnWriteArrayList<DisputeDto>()
    val histories = ConcurrentHashMap<String, List<HistoryDto>>()

    /** When true, the login request never gets a response, so the client times out. */
    @Volatile
    var stallLogin = false

    /** When true, submitting a dispute returns 409 Conflict. */
    @Volatile
    var rejectSubmitsWithConflict = false

    val baseUrl: String

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = route(request)
        }
        server.start()
        baseUrl = server.url("/").toString()
    }

    /** Returns the requests made with [method] to [path], in order. */
    fun callsTo(method: String, path: String): List<ApiCall> =
        calls.filter { it.method == method && it.path == path }

    override fun close() {
        if (!closed) {
            closed = true
            server.shutdown()
        }
    }

    private fun route(request: RecordedRequest): MockResponse {
        val call = ApiCall(
            method = request.method.orEmpty(),
            path = request.path.orEmpty(),
            authorization = request.getHeader("Authorization"),
            body = request.body.clone().readUtf8(),
        )
        calls.add(call)
        return when {
            call.method == "POST" && call.path == "/api/auth/login" -> login(call.body)
            call.authorization != "Bearer $TOKEN" -> MockResponse().setResponseCode(401)
            else -> authorized(call)
        }
    }

    private fun login(body: String): MockResponse {
        if (stallLogin) return MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        val credentials = gson.fromJson(body, LoginRequestDto::class.java)
        return if (credentials.email == EMAIL && credentials.password == PASSWORD) {
            json(LoginResponseDto(TOKEN))
        } else {
            MockResponse().setResponseCode(401)
        }
    }

    private fun authorized(call: ApiCall): MockResponse {
        val segments = call.path.split("/")
        return when {
            call.method == "POST" && call.path == "/api/auth/logout" -> MockResponse().setResponseCode(204)
            call.method == "GET" && call.path == "/api/transactions" -> json(transactions.toList())
            call.method == "GET" && call.path == "/api/disputes" -> json(disputes.toList())
            call.method == "POST" && call.path == "/api/disputes" -> submit(call.body)
            call.method == "POST" && call.path.matches(WITHDRAW_PATH) -> withdraw(segments[3])
            call.method == "GET" && call.path.matches(HISTORY_PATH) ->
                json(histories[segments[3]].orEmpty())
            else -> MockResponse().setResponseCode(404)
        }
    }

    private fun submit(body: String): MockResponse {
        if (rejectSubmitsWithConflict) return MockResponse().setResponseCode(409)
        val request = gson.fromJson(body, SubmitDisputeRequestDto::class.java)
        val dispute = DisputeDto(
            id = "dispute-${disputes.size + 1}",
            transactionId = request.transactionId,
            reason = request.reason,
            description = request.description,
            disputedAmountMinor = request.disputedAmountMinor,
            status = "SUBMITTED",
            createdAt = CREATED_AT,
            updatedAt = CREATED_AT,
        )
        disputes.add(dispute)
        return json(dispute, code = 201)
    }

    private fun withdraw(id: String): MockResponse {
        val index = disputes.indexOfFirst { it.id == id }
        if (index < 0) return MockResponse().setResponseCode(404)
        val updated = disputes[index].copy(status = "WITHDRAWN", updatedAt = CREATED_AT)
        disputes[index] = updated
        return json(updated)
    }

    private fun json(body: Any, code: Int = 200): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(gson.toJson(body))

    companion object {
        const val EMAIL = "customer@example.com"
        const val PASSWORD = "correct-horse"
        const val TOKEN = "test-token"

        private val WITHDRAW_PATH = Regex("/api/disputes/[^/]+/withdraw")
        private val HISTORY_PATH = Regex("/api/disputes/[^/]+/history")
    }
}

/**
 * The app's real object graph, with an in-memory database and the API pointed at [baseUrl].
 *
 * This mirrors [com.example.disputeportal.AppContainer] without the debug URL and file-backed database.
 */
class TestApp(context: Context, baseUrl: String) : AutoCloseable {
    private val database: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val viewModel: MainViewModel

    init {
        val session = Session()
        val repository = DisputeRepository(api = buildApi(session, baseUrl), db = database)
        viewModel = MainViewModel(session, repository)
    }

    override fun close() {
        database.close()
    }
}

/** Sets the app's root composable with [app]'s view model. */
fun ComposeContentTestRule.showApp(app: TestApp) {
    setContent { DisputeTheme { DisputeApp(app.viewModel) } }
}

/** Types the credentials and taps Sign in. */
fun ComposeContentTestRule.signIn(
    email: String = FakeBackend.EMAIL,
    password: String = FakeBackend.PASSWORD,
) {
    onNodeWithText("Email").performTextInput(email)
    onNodeWithText("Password").performTextInput(password)
    onNodeWithText("Sign in").performClick()
}

/** Signs in with valid credentials and waits until the signed-in screen is shown. */
fun ComposeContentTestRule.showAppAndSignIn(app: TestApp) {
    showApp(app)
    signIn()
    waitUntilExactlyOneExists(hasText("Sign out"), WAIT_MS)
}
