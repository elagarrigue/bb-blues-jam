package com.bbbjam.core.ui.status

import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.ui.presenter.UiModel

/**
 * A jam's status as the badge draws it: [label] ("Creada"; the badge draws it uppercase) and whether it is
 * published.
 */
data class JamStatusBadgeUiModel(val label: String, val isPublished: Boolean) : UiModel

/** The badge of [status]: BORRADOR, CREADA and PUBLICADA (D-26). Pure. */
fun jamStatusBadge(status: JamStatus): JamStatusBadgeUiModel = when (status) {
    JamStatus.DRAFT -> JamStatusBadgeUiModel(JamStatusBadgeCopy.DRAFT, isPublished = false)
    JamStatus.CREATED -> JamStatusBadgeUiModel(JamStatusBadgeCopy.CREATED, isPublished = false)
    JamStatus.PUBLISHED -> JamStatusBadgeUiModel(JamStatusBadgeCopy.PUBLISHED, isPublished = true)
}
