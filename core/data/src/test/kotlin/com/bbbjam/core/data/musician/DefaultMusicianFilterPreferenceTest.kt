package com.bbbjam.core.data.musician

import com.bbbjam.core.data.admin.AdminCredentialStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The preference over a temp-file DataStore from the production factory; cancelling the scope is a restart. */
class DefaultMusicianFilterPreferenceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file get() = File(folder.root, "datastore/musician_filter.preferences_pb")

    private fun <T> withPreference(block: suspend (DefaultMusicianFilterPreference) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val preference = DefaultMusicianFilterPreference(AdminCredentialStore.dataStore(scope) { file })
            return runBlocking { block(preference) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `an empty store has no selection`() {
        withPreference { assertNull(it.observeSelected().first()) }
    }

    @Test
    fun `a selected name survives a restart`() {
        withPreference { it.select("Juan") }
        withPreference { assertEquals("Juan", it.observeSelected().first()) }
    }

    @Test
    fun `selecting null or blank clears it, also after a restart`() {
        withPreference {
            it.select("Juan")
            it.select("  ")
            assertNull(it.observeSelected().first())
            it.select("Ana")
            it.select(null)
        }
        withPreference { assertNull(it.observeSelected().first()) }
    }

    @Test
    fun `a corrupt file reads as no selection`() {
        checkNotNull(file.parentFile).mkdirs()
        file.writeBytes(byteArrayOf(0x0A, 0x7F, 0x13, 0x00, 0x42, 0x42))
        withPreference { assertNull(it.observeSelected().first()) }
    }
}
