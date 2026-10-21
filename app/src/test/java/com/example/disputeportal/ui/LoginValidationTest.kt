package com.example.disputeportal.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginValidationTest {

    @Test
    fun acceptsWellFormedEmail() {
        assertTrue(isValidEmail("thandi@example.com"))
    }

    @Test
    fun trimsWhitespaceAroundEmail() {
        assertTrue(isValidEmail("  ben@example.com  "))
    }

    @Test
    fun rejectsMalformedEmails() {
        listOf("", "ben", "ben@", "@example.com", "ben@example", "ben smith@example.com", "ben@@example.com")
            .forEach { assertFalse("expected invalid: '$it'", isValidEmail(it)) }
    }

    @Test
    fun rejectsPasswordShorterThanMinimum() {
        assertFalse(isValidPassword("1234567"))
    }

    @Test
    fun acceptsPasswordAtMinimumLength() {
        assertTrue(isValidPassword("12345678"))
    }

    @Test
    fun rejectsEmptyPassword() {
        assertFalse(isValidPassword(""))
    }
}
