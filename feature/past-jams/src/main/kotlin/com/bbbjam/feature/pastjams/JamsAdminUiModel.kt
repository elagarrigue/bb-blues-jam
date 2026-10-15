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
) : UiModel

/**
 * One future jam. [date] is its identity (the list key). [heldBack] is the line of a held-back jam
 * (its status cannot be changed until it is the upcoming one) and [controls] are the upcoming jam's
 * status actions and publication; exactly one of them is set.
 */
data class FutureJamUiModel(
    val date: LocalDate,
    val dateLabel: String,
    val venue: String,
    val badge: JamStatusBadgeUiModel,
    val note: String,
    val songCount: String?,
    val heldBack: String?,
    val controls: JamControlsUiModel?,
) : UiModel

/** The upcoming jam's controls: the status change and, while it applies, the publication. */
data class JamControlsUiModel(val status: JamStatusUiModel, val publish: PublishUiModel?) : UiModel
