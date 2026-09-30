package com.bbbjam.feature.info

import com.bbbjam.core.ui.link.ExternalLinkOpener
import com.bbbjam.feature.info.di.infoModule
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class InfoModuleTest {
    @Test
    fun `infoModule resolves a new presenter each time once a link opener is bound`() {
        val openerModule = module { single<ExternalLinkOpener> { ExternalLinkOpener { true } } }
        val app = koinApplication { modules(infoModule, openerModule) }
        try {
            val first = app.koin.get<InfoPresenter>()
            val second = app.koin.get<InfoPresenter>()
            // factory, not single: presenters hold no state; the composition does.
            assertNotSame(first, second)
        } finally {
            app.close()
        }
    }
}
