package com.bbbjam.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsScriptEndpointTest {

    @Test
    fun `an https URL is configured, trimmed`() {
        val endpoint = AppsScriptEndpoint.of("  https://example.invalid/macros/s/abc/exec \n")

        assertTrue(endpoint.isConfigured)
        assertEquals("https://example.invalid/macros/s/abc/exec", endpoint.url)
    }

    @Test
    fun `a blank, non-https or malformed URL is not configured`() {
        listOf(
            "",
            "   ",
            "http://example.invalid/exec",
            "example.invalid/exec",
            "ftp://example.invalid",
            "https://",
        ).forEach {
            val endpoint = AppsScriptEndpoint.of(it)
            assertFalse(it, endpoint.isConfigured)
            assertNull(it, endpoint.url)
        }
    }

    @Test
    fun `toString never prints the URL`() {
        assertEquals("AppsScriptEndpoint(configured)", AppsScriptEndpoint.of("https://example.invalid/exec").toString())
        assertEquals("AppsScriptEndpoint(not configured)", AppsScriptEndpoint.of("").toString())
    }
}
