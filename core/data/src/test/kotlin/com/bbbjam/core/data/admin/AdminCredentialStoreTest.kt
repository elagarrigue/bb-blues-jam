package com.bbbjam.core.data.admin

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The DataStore file on the JVM, through the production factory; no Android, no Robolectric. */
class AdminCredentialStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `an empty store is logged out`() = runBlocking {
        StoreHarness(folder.root).use { harness ->
            assertFalse(harness.store.observeIsAdmin().first())
            assertNull(harness.store.passphrase())
        }
    }

    @Test
    fun `a saved passphrase survives a restart, a new instance over the same file`() = runBlocking {
        StoreHarness(folder.root).use { harness ->
            harness.store.save(TEST_VALUE)
            assertTrue(harness.store.observeIsAdmin().first())
        }

        StoreHarness(folder.root).use { restarted ->
            assertTrue(restarted.store.observeIsAdmin().first())
            assertEquals(TEST_VALUE, restarted.store.passphrase())
        }
    }

    @Test
    fun `clear logs out, also after a restart`() = runBlocking {
        StoreHarness(folder.root).use { harness ->
            harness.store.save(TEST_VALUE)
            harness.store.clear()
            assertFalse(harness.store.observeIsAdmin().first())
        }

        StoreHarness(folder.root).use { restarted ->
            assertFalse(restarted.store.observeIsAdmin().first())
            assertNull(restarted.store.passphrase())
        }
    }

    @Test
    fun `a corrupt file reads as logged out`() = runBlocking {
        val harness = StoreHarness(folder.root)
        checkNotNull(harness.file.parentFile).mkdirs()
        harness.file.writeBytes(byteArrayOf(0x0A, 0x7F, 0x13, 0x00, 0x42, 0x42))

        harness.use {
            assertFalse(it.store.observeIsAdmin().first())
            assertNull(it.store.passphrase())
        }
    }

    @Test
    fun `a blank stored value is not admin mode`() = runBlocking {
        StoreHarness(folder.root).use { harness ->
            harness.store.save("   ")
            assertFalse(harness.store.observeIsAdmin().first())
            assertNull(harness.store.passphrase())
        }
    }

    private companion object {
        const val TEST_VALUE = "not-a-real-passphrase"
    }
}
