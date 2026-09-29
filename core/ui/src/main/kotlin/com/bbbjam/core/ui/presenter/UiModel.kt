package com.bbbjam.core.ui.presenter

import androidx.compose.runtime.Immutable

/**
 * What a presenter returns: display values and [EventHandler]s only. No repositories, no Android
 * types, no raw lambdas.
 */
@Immutable
interface UiModel
