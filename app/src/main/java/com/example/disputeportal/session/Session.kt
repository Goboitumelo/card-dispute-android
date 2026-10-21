package com.example.disputeportal.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Held in memory only. The password is never written to disk.
data class Login(val email: String, val password: String)

class Session {
    private val _login = MutableStateFlow<Login?>(null)
    val login: StateFlow<Login?> = _login.asStateFlow()

    fun signIn(email: String, password: String) {
        _login.value = Login(email, password)
    }

    fun signOut() {
        _login.value = null
    }
}
