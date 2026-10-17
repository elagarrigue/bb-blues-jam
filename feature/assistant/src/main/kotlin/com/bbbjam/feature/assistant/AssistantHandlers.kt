package com.bbbjam.feature.assistant

import com.bbbjam.core.ui.jamform.JamFormUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.feature.assistant.apply.ApplySummaryUiModel
import com.bbbjam.feature.assistant.apply.ConfirmationUiModel
import com.bbbjam.feature.assistant.apply.TargetActionUiModel
import com.bbbjam.feature.assistant.chat.AssistantChat
import com.bbbjam.feature.assistant.jam.JamActionUiModel
import com.bbbjam.feature.assistant.run.ApplyMode

/** The handlers the mapping hands to the model; the presenter builds them over [AssistantChat]. */
internal class AssistantHandlers(
    val cancel: (Long) -> EventHandler<ReplyUiModel.Pending.Event>,
    val retry: (Long) -> EventHandler<RetryUiModel.Event>,
    val composer: EventHandler<ComposerUiModel.Event>,
    val status: EventHandler<StatusUiModel.Banner.Event>,
    val apply: (Long, ApplyMode) -> EventHandler<TargetActionUiModel.Event>,
    val confirmation: (Long) -> EventHandler<ConfirmationUiModel.Event>,
    val stop: (Long) -> EventHandler<ApplySummaryUiModel.Event>,
    val createForm: (Long) -> EventHandler<JamFormUiModel.Event>,
    val jam: (Long) -> EventHandler<JamActionUiModel.Event>,
    val jamConfirmation: (Long) -> EventHandler<ConfirmationUiModel.Event>,
)
