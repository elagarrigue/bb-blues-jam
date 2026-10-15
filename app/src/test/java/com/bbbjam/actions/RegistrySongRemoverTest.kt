package com.bbbjam.actions

import com.bbbjam.core.data.setlist.RemoveSongOutcome
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.data.setlist.SetlistSongRemover
import com.bbbjam.core.model.SongId
import com.bbbjam.di.appModule
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class RegistrySongRemoverTest {
    private val date = LocalDate.of(2026, 10, 31)
    private val songId = SongId("red-house")

    @Test
    fun `removeSong reaches the repository once with the arguments unchanged and returns its outcome`() = runBlocking {
        val repository = RecordingSetlistRepository()
        val remover = RegistrySongRemover(SetlistActionRegistry(repository))

        val outcome = remover.removeSong(date, songId)

        assertEquals(RemoveSongOutcome.Removed, outcome)
        assertEquals(listOf(RecordedCall(SetlistMutation.REMOVE_SONG, listOf(date, songId))), repository.calls)
    }

    @Test
    fun `app module binds the contract to the registry-backed remover`() {
        val repository = RecordingSetlistRepository()
        val application = koinApplication {
            allowOverride(true)
            modules(appModule, module { single<SetlistRepository> { repository } })
        }
        try {
            val remover = application.koin.get<SetlistSongRemover>()
            assertTrue(remover is RegistrySongRemover)
            runBlocking { remover.removeSong(date, songId) }
            assertEquals(SetlistMutation.REMOVE_SONG, repository.calls.single().mutation)
        } finally {
            application.close()
        }
    }
}
