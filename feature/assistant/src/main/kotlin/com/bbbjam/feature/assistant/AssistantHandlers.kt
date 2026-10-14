package com.bbbjam.feature.assistant

import com.bbbjam.core.ui.presenter.EventHandler

/** The handlers the mapping hands to the model; the presenter builds them over [AssistantChat]. */
internal class AssistantHandlers(
    val cancel: (Long) -> EventHandler<ReplyUiModel.Pending.Event>,
    val retry: (Long) -> EventHandler<RetryUiModel.Event>,
    val composer: EventHandler<ComposerUiModel.Event>,
    val status: EventHandler<StatusUiModel.Banner.Event>,
    val apply: (Long) -> EventHandler<ApplyUiModel.Action.Event>,
    val confirmation: (Long) -> EventHandler<ConfirmationUiModel.Event>,
    val stop: (Long) -> EventHandler<ApplySummaryUiModel.Event>,
    val jam: (Long) -> EventHandler<JamActionUiModel.Event>,
    val jamConfirmation: (Long) -> EventHandler<ConfirmationUiModel.Event>,
)
