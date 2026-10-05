package com.bbbjam.debug

import com.bbbjam.core.model.Jam
import org.koin.core.Koin
import org.koin.core.module.Module

/**
 * Release variant of the debug overrides: none. The demo upcoming jam (debug-demo-upcoming-jam) and
 * the debug admin session (debug-admin-session) live only in `app/src/debug`, so a release build
 * cannot compile or ship them.
 */
internal fun Koin.debugOverrides(): List<Module> = emptyList()

/** No jam is a demo in release. */
internal fun Jam.isDemo(): Boolean = false

/** Release never starts in forced admin mode. */
internal fun debugAdminLogSuffix(): String = ""
