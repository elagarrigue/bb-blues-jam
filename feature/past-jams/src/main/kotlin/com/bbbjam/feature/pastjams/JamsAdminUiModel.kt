package com.bbbjam.feature.pastjams

import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.status.JamStatusBadgeUiModel
import java.time.LocalDate

/**
 * The admin's part of the Jams tab (D-15: a state of the same screen, null for musicians): the
 * upcoming jam and every held-back future jam as [future] cards in date order, BORRADOR included
 * (D-26), above the past jams. [noFuture] is the line shown when there is no future jam.
 */
data class JamsAdminUiModel(
    val upcomingHeading: String,
    val pastHeading: String,
    val noFuture: String?,
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
data class FutureJamUiModel(
    val date: LocalDate,
    val dateLabel: String,
    val venue: String,
    val badge: JamStatusBadgeUiModel,
    val note: String,
    val songCount: String?,
    val heldBack: String?,
    val musicianNote: String?,
    val controls: JamControlsUiModel,
    val manage: JamManageUiModel,
) : UiModel

/** The upcoming jam's controls: the status change and, while it applies, the publication. */
data class JamControlsUiModel(val status: JamStatusUiModel, val publish: PublishUiModel?) : UiModel
