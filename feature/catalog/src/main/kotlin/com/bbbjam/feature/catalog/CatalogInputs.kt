package com.bbbjam.feature.catalog

import com.bbbjam.core.data.catalog.CatalogSnapshot
import com.bbbjam.core.data.jams.JamsSnapshot
import java.time.Instant

/** What the presenter reads from outside the controls, to build one [CatalogUiModel.Content]. */
internal class CatalogInputs(
    val catalog: CatalogSnapshot?,
    val jams: JamsSnapshot?,
    val now: Instant,
    val pulling: Boolean,
    val onRetry: () -> Unit,
    val onPull: () -> Unit,
)
