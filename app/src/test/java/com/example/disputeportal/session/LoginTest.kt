package com.example.disputeportal.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginTest {

    @Test
    fun toStringDoesNotExposeToken() {
        val text = Login("thandi@example.com", "opaque-token-value").toString()

        assertTrue(text.contains("thandi@example.com"))
        assertFalse(text.contains("opaque-token-value"))
    }
}
