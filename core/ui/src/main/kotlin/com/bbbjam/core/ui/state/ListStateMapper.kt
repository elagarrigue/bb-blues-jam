package com.bbbjam.core.ui.state

import com.bbbjam.core.ui.presenter.EventHandler
import java.time.Duration

/**
 * The error block for a list that has nothing cached and whose read failed. [title] is the
 * feature's ("No pudimos cargar la próxima jam"); the message depends only on [isOffline]. The
 * retry handler calls [onRetry].
 */
fun listError(title: String, isOffline: Boolean, onRetry: () -> Unit): ListErrorUiModel = ListErrorUiModel(
    title = title,
    message = if (isOffline) ListStateCopy.ERROR_OFFLINE else ListStateCopy.ERROR_OTHER,
    retryLabel = ListStateCopy.RETRY,
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
    title = if (isOffline) ListStateCopy.NOTICE_OFFLINE else ListStateCopy.NOTICE_OTHER,
    detail = if (isRefreshing) {
        ListStateCopy.REFRESHING
    } else {
        ListStateCopy.showingSaved(ListStateCopy.age(age.coerceAtLeast(Duration.ZERO).toMinutes()))
    },
    retryLabel = if (isRefreshing) null else ListStateCopy.RETRY,
    events = EventHandler { event ->
        when (event) {
            StalenessNoticeUiModel.Event.Retry -> onRetry()
        }
    },
)
