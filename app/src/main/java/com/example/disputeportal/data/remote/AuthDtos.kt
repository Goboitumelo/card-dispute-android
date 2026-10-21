package com.example.disputeportal.data.remote

/**
 * Body of a login request.
 *
 * @property email the customer's email address
 * @property password the customer's password, sent once to obtain a token
 */
data class LoginRequestDto(val email: String, val password: String) {
    /** Redacts the password so it cannot leak into logs or debugger output. */
    override fun toString(): String = "LoginRequestDto(email=$email, password=***)"
}

/**
 * Response to a successful login.
 *
 * @property token the bearer token to send with later requests
 */
data class LoginResponseDto(val token: String)
