package com.example.disputeportal.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Held in memory only. The token is never written to disk.
data class Login(val email: String, val token: String) {
    /** Redacts the token so it cannot leak into logs or debugger output. */
    override fun toString(): String = "Login(email=$email, token=***)"
}

class Session {
    private val _login = MutableStateFlow<Login?>(null)
    val login: StateFlow<Login?> = _login.asStateFlow()

    /**
     * Stores the bearer token used to authenticate later requests.
     *
     * @param email the user's email address
     * @param token the bearer token issued at sign-in
     */
    fun signIn(email: String, token: String) {
        _login.value = Login(email, token)
    }

    /** Clears the stored credentials. */
    fun signOut() {
        _login.value = null
    }
}
