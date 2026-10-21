package com.example.disputeportal.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Held in memory only. The password is never written to disk.
data class Login(val email: String, val password: String)

class Session {
    private val _login = MutableStateFlow<Login?>(null)
    val login: StateFlow<Login?> = _login.asStateFlow()

    /**
     * Stores the credentials used to authenticate later requests.
     *
     * @param email the user's email address
     * @param password the user's password
     */
    fun signIn(email: String, password: String) {
        _login.value = Login(email, password)
    }

    /** Clears the stored credentials. */
    fun signOut() {
        _login.value = null
    }
}
