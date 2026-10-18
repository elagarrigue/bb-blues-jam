package com.bbbjam.core.ui.state

import com.bbbjam.core.ui.R
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.text.UiText
import java.time.Duration

/**
 * The error block for a list that has nothing cached and whose read failed. [title] is the
 * feature's ("No pudimos cargar la próxima jam"); the message depends only on [isOffline]. The
 * retry handler calls [onRetry].
 */
fun listError(title: UiText, isOffline: Boolean, onRetry: () -> Unit): ListErrorUiModel = ListErrorUiModel(
    title = title,
    message = UiText.res(if (isOffline) R.string.list_state_error_offline else R.string.list_state_error_other),
    retryLabel = UiText.res(R.string.list_state_retry),
    events = EventHandler { event ->
        when (event) {
            ListErrorUiModel.Event.Retry -> onRetry()
        }
    },
)

/**
 * The notice above cached data whose latest refresh failed. [age] is how old the data is; a
 * negative age (a clock that moved back) reads as zero. While [isRefreshing], the detail is
 * "Actualizando…" and there is no retry action. The retry handler calls [onRetry].
 */
fun stalenessNotice(
    isOffline: Boolean,
    age: Duration,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
): StalenessNoticeUiModel = StalenessNoticeUiModel(
    title = UiText.res(if (isOffline) R.string.list_state_notice_offline else R.string.list_state_notice_other),
    detail = if (isRefreshing) {
        UiText.res(R.string.list_state_refreshing)
    } else {
        UiText.res(R.string.list_state_showing_saved, age(age.coerceAtLeast(Duration.ZERO).toMinutes()))
    },
    retryLabel = if (isRefreshing) null else UiText.res(R.string.list_state_retry),
    events = EventHandler { event ->
        when (event) {
            StalenessNoticeUiModel.Event.Retry -> onRetry()
        }
    },
)

/**
 * "hace menos de un minuto", "hace 1 minuto", "hace n minutos", "hace 1 hora", "hace n horas",
 * "hace 1 día", "hace n días": whole units, rounded down.
 */
private fun age(minutes: Long): UiText = when {
    minutes < 1 -> UiText.res(R.string.list_state_age_under_a_minute)
    minutes < MINUTES_PER_HOUR -> UiText.plural(R.plurals.list_state_age_minutes, minutes.toInt())
    minutes < MINUTES_PER_DAY -> UiText.plural(R.plurals.list_state_age_hours, (minutes / MINUTES_PER_HOUR).toInt())
    else -> UiText.plural(R.plurals.list_state_age_days, (minutes / MINUTES_PER_DAY).toInt())
}

private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR
