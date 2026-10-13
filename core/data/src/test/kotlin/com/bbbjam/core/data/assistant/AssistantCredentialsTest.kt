package com.bbbjam.core.data.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantCredentialsTest {
    @Test
    fun `blank and whitespace keys are not configured`() {
        assertFalse(AssistantCredentials.of("").isConfigured)
        assertFalse(AssistantCredentials.of("   \t ").isConfigured)
    }

    @Test
    fun `a key is configured and is trimmed`() {
        val credentials = AssistantCredentials.of("  sk-secret  ")

        assertTrue(credentials.isConfigured)
        assertEquals("sk-secret", credentials.key)
    }

    @Test
    fun `toString never contains the key`() {
        val secret = "sk-very-secret-value"

        assertEquals("AssistantCredentials(configured)", AssistantCredentials.of(secret).toString())
        assertEquals("AssistantCredentials(not configured)", AssistantCredentials.of("").toString())
        assertFalse("$secret" in AssistantCredentials.of(secret).toString())
    }
}
