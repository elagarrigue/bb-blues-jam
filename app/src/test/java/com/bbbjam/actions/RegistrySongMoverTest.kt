package com.bbbjam.actions

import com.bbbjam.core.data.setlist.MoveSongOutcome
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.data.setlist.SetlistSongMover
import com.bbbjam.core.model.SongId
import com.bbbjam.di.appModule
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class RegistrySongMoverTest {
    private val date = LocalDate.of(2026, 10, 31)
    private val songId = SongId("red-house")

    @Test
    fun `moveSong reaches the repository once with the arguments unchanged and returns its outcome`() = runBlocking {
        val repository = RecordingSetlistRepository()
        val mover = RegistrySongMover(SetlistActionRegistry(repository))

        val outcome = mover.moveSong(date, songId, 4)

        assertEquals(MoveSongOutcome.Moved, outcome)
        assertEquals(listOf(RecordedCall(SetlistMutation.MOVE_SONG, listOf(date, songId, 4))), repository.calls)
    }

    @Test
    fun `app module binds the contract to the registry-backed mover`() {
        val repository = RecordingSetlistRepository()
        val application = koinApplication {
            allowOverride(true)
            modules(appModule, module { single<SetlistRepository> { repository } })
        }
        try {
            val mover = application.koin.get<SetlistSongMover>()
            assertTrue(mover is RegistrySongMover)
            runBlocking { mover.moveSong(date, songId, 2) }
            assertEquals(SetlistMutation.MOVE_SONG, repository.calls.single().mutation)
        } finally {
            application.close()
        }
    }
}
