package com.bbbjam.actions

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.setlist.CreateJamOutcome
import com.bbbjam.core.data.setlist.JamCreator
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.di.appModule
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class RegistryJamCreatorTest {
    private val date = LocalDate.of(2026, 11, 28)
    private val time = LocalTime.of(21, 0)

    @Test
    fun `createJam reaches the repository once with the arguments unchanged and returns its outcome`() = runBlocking {
        val repository = RecordingSetlistRepository()
        val creator = RegistryJamCreator(SetlistActionRegistry(repository))

        val outcome = creator.createJam(date, time, "La Macanuda")

        assertEquals(CreateJamOutcome.Created(date, time, "La Macanuda"), outcome)
        assertEquals(
            listOf(RecordedCall(SetlistMutation.CREATE_JAM, listOf(date, time, "La Macanuda"))),
            repository.calls,
        )
    }

    @Test
    fun `refusals and an outdated script are returned unchanged`() = runBlocking {
        val repository = RecordingSetlistRepository()
        val creator = RegistryJamCreator(SetlistActionRegistry(repository))
        for (expected in listOf(
            CreateJamOutcome.NotCreated(WriteOutcome.Rejected("jam_exists")),
            CreateJamOutcome.ScriptOutdated,
        )) {
            repository.createOutcome = expected
            assertEquals(expected, creator.createJam(date, time, "Bar"))
        }
    }

    @Test
    fun `app module binds the contract to the registry-backed creator`() {
        val repository = RecordingSetlistRepository()
        val application = koinApplication {
            allowOverride(true)
            modules(appModule, module { single<SetlistRepository> { repository } })
        }
        try {
            val creator = application.koin.get<JamCreator>()
            assertTrue(creator is RegistryJamCreator)
            runBlocking { creator.createJam(date, time, "Bar") }
            assertEquals(SetlistMutation.CREATE_JAM, repository.calls.single().mutation)
        } finally {
            application.close()
        }
    }
}
