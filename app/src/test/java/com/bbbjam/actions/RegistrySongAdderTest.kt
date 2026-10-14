package com.bbbjam.actions

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.setlist.AddSongOutcome
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.data.setlist.SetlistSongAdder
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import com.bbbjam.di.appModule
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class RegistrySongAdderTest {
    private val date = LocalDate.of(2026, 10, 31)
    private val songId = SongId("red-house")
    private val key = Key("Bm")

    @Test
    fun `addSong reaches the repository once with the arguments unchanged and returns its outcome`() = runBlocking {
        val repository = RecordingSetlistRepository()
        val adder = RegistrySongAdder(SetlistActionRegistry(repository))

        val outcome = adder.addSong(date, songId, key)

        assertEquals(AddSongOutcome.Added(7), outcome)
        assertEquals(listOf(RecordedCall(SetlistMutation.ADD_SONG, listOf(date, songId, key))), repository.calls)
    }

    @Test
    fun `a refusal is returned unchanged`() = runBlocking {
        val repository = RecordingSetlistRepository()
        repository.addOutcome = AddSongOutcome.NotAdded(WriteOutcome.Rejected("song_already_in_setlist"))
        val adder = RegistrySongAdder(SetlistActionRegistry(repository))

        assertEquals(repository.addOutcome, adder.addSong(date, songId, key))
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun `app module binds the contract to the registry-backed adder`() {
        val repository = RecordingSetlistRepository()
        val application = koinApplication {
            allowOverride(true)
            modules(appModule, module { single<SetlistRepository> { repository } })
        }
        try {
            val adder = application.koin.get<SetlistSongAdder>()
            assertTrue(adder is RegistrySongAdder)
            runBlocking { adder.addSong(date, songId, key) }
            assertEquals(SetlistMutation.ADD_SONG, repository.calls.single().mutation)
        } finally {
            application.close()
        }
    }
}
