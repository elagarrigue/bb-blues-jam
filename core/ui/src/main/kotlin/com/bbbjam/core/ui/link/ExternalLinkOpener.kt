package com.bbbjam.core.ui.link

/**
 * Opens a web link outside the app (browser or the site's own app). A contract in `:core:ui` so any
 * feature can open links without importing another feature (D-03); `:app` binds the Android
 * implementation. Presenters call it from event handlers, never Android APIs directly, so they stay
 * JVM-testable with a fake.
 */
fun interface ExternalLinkOpener {
    /** Returns false when nothing on the device can open [url]. */
    fun open(url: String): Boolean
}
