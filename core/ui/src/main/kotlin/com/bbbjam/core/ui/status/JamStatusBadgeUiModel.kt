package com.bbbjam.core.ui.status

import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.ui.R
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.text.UiText

/**
 * A jam's status as the badge draws it: [label] ("Creada"; the badge draws it uppercase) and whether it is
 * published.
 */
data class JamStatusBadgeUiModel(val label: UiText, val isPublished: Boolean) : UiModel

/** The badge of [status]: BORRADOR, CREADA and PUBLICADA (D-26). Pure. */
fun jamStatusBadge(status: JamStatus): JamStatusBadgeUiModel = when (status) {
    JamStatus.DRAFT -> JamStatusBadgeUiModel(UiText.res(R.string.jam_status_draft), isPublished = false)
    JamStatus.CREATED -> JamStatusBadgeUiModel(UiText.res(R.string.jam_status_created), isPublished = false)
    JamStatus.PUBLISHED -> JamStatusBadgeUiModel(UiText.res(R.string.jam_status_published), isPublished = true)
}
