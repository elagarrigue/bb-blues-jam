package com.bbbjam.debug

import com.bbbjam.core.model.Jam
import org.koin.core.Koin
import org.koin.core.module.Module

/**
 * Release variant of the debug overrides: none. The demo upcoming jam (debug-demo-upcoming-jam)
 * lives only in `app/src/debug`, so a release build cannot compile or ship it.
 */
internal fun Koin.debugOverrides(): List<Module> = emptyList()

/** No jam is a demo in release. */
internal fun Jam.isDemo(): Boolean = false
