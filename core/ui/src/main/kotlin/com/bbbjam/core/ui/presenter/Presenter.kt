package com.bbbjam.core.ui.presenter

import androidx.compose.runtime.Composable

/**
 * A composable presenter (D-02): state lives in the Compose runtime and [present] returns an
 * immutable [UiModel]. Dependencies arrive through the constructor; runtime inputs as [Params].
 */
interface Presenter<Model : UiModel, Params> {
    @Composable
    fun present(params: Params): Model
}
