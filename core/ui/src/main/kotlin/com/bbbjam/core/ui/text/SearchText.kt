package com.bbbjam.core.ui.text

import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/**
 * Lowercase with accents removed (`Batería` becomes `bateria`), for matching and sorting only. Shared by
 * the catalog picker and the Temas tab (`admin-catalog-tab`).
 */
fun String.searchKey(): String =
    COMBINING_MARKS.replace(Normalizer.normalize(this, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
