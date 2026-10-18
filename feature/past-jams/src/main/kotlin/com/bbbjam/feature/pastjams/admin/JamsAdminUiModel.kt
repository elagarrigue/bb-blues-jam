package com.bbbjam.feature.pastjams.admin

import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.pastjams.create.NewJamUiModel
import com.bbbjam.feature.pastjams.edit.DeleteJamUiModel
import com.bbbjam.feature.pastjams.edit.EditJamFormUiModel
import com.bbbjam.feature.pastjams.edit.JamManageUiModel
import com.bbbjam.feature.pastjams.status.JamStatusUiModel
import com.bbbjam.feature.pastjams.status.PublishUiModel
import java.time.LocalDate

/**
 * The admin's part of the Jams tab (D-15: a state of the same screen, null for musicians): the
 * upcoming jam and every held-back future jam as [future] cards in date order, BORRADOR included
 * (D-26), above the past jams. [noFuture] is the line shown when there is no future jam.
 */
internal data class JamsAdminUiModel(
    val upcomingHeading: UiText,
    val pastHeading: UiText,
    val noFuture: UiText?,
    val future: List<FutureJamUiModel>,
    val newJam: NewJamUiModel,
    val editForm: EditJamFormUiModel?,
    val deleteConfirm: DeleteJamUiModel?,
) : UiModel

/**
 * One future jam. [date] is its identity (the list key). [heldBack] is the line of a held-back jam and
 * [musicianNote] the extra line of a visible held-back jam behind a BORRADOR upcoming jam (D-26). [controls]
 * are the status actions (every card) and, on the upcoming card only, the publication; [manage] are the edit
 * and delete actions (`admin-edit-delete-jam`).
 */
internal data class FutureJamUiModel(
    val date: LocalDate,
    val dateLabel: UiText,
    val venue: String,
    val note: UiText,
    val songCount: UiText?,
    val heldBack: UiText?,
    val musicianNote: UiText?,
    val controls: JamControlsUiModel,
    val manage: JamManageUiModel,
) : UiModel

/** The upcoming jam's controls: the status change and, while it applies, the publication. */
internal data class JamControlsUiModel(val status: JamStatusUiModel, val publish: PublishUiModel?) : UiModel
