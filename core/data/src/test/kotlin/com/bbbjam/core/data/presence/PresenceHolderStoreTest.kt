package com.bbbjam.core.data.presence

import com.bbbjam.core.data.admin.AdminCredentialStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The holder id over a temp-file DataStore from the production factory; cancelling the scope is a restart. */
class PresenceHolderStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun <T> withStore(dir: File = folder.root, block: suspend (PresenceHolderStore) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val file = File(dir, "${PresenceHolderStore.FILE_NAME}.preferences_pb")
            return runBlocking { block(PresenceHolderStore(AdminCredentialStore.dataStore(scope) { file })) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `the id is created once and is a valid uuid the server accepts`() {
        withStore {
            val first = checkNotNull(it.holderId())
            assertEquals(first, it.holderId())
            UUID.fromString(first)
            assert(Regex("^[A-Za-z0-9-]{16,64}$").matches(first))
        }
    }

    @Test
    fun `the id is stable across a restart`() {
        val first = withStore { it.holderId() }
        assertEquals(first, withStore { it.holderId() })
    }

    @Test
    fun `another install gets another id`() {
        val first = withStore { it.holderId() }
        assertNotEquals(first, withStore(folder.newFolder()) { it.holderId() })
    }
}
