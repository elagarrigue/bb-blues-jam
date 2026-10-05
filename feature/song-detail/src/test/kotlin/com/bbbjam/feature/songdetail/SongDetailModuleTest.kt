package com.bbbjam.feature.songdetail

import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.feature.songdetail.di.songDetailModule
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class SongDetailModuleTest {
    @Test
    fun `songDetailModule resolves a new presenter each time once the data bindings exist`() {
        val dataBindings = module { single<JamsRepository> { FakeJamsRepository() } }
        val app = koinApplication { modules(songDetailModule, dataBindings) }
        try {
            // factory, not single: presenters hold no state; the composition does.
            assertNotSame(app.koin.get<SongDetailPresenter>(), app.koin.get<SongDetailPresenter>())
        } finally {
            app.close()
        }
    }
}
