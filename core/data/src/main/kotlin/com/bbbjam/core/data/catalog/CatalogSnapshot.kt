package com.bbbjam.core.data.catalog

import com.bbbjam.core.data.Freshness
import com.bbbjam.core.model.Song

/** The cached catalog, in Sheet order, and how current it is. */
data class CatalogSnapshot(val songs: List<Song>, val freshness: Freshness)
