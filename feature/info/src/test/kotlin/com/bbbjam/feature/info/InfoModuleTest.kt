package com.bbbjam.feature.info

import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.ui.link.ExternalLinkOpener
import com.bbbjam.feature.info.di.infoModule
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class InfoModuleTest {
    @Test
    fun `infoModule resolves new presenters each time once a link opener and a session are bound`() {
        val bindings = module {
            single<ExternalLinkOpener> { ExternalLinkOpener { true } }
            single<AdminSession> { FakeAdminSession() }
        }
        val app = koinApplication { modules(infoModule, bindings) }
        try {
            // factory, not single: presenters hold no state; the composition does.
            assertNotSame(app.koin.get<InfoPresenter>(), app.koin.get<InfoPresenter>())
            assertNotSame(app.koin.get<AdminLoginPresenter>(), app.koin.get<AdminLoginPresenter>())
        } finally {
            app.close()
        }
    }
}
