package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal fun removalHandler(
    setlist: SetlistRepository,
    scope: CoroutineScope,
    isConfirming: (String) -> Boolean,
    updateConfirmation: (String?) -> Unit,
): (LocalDate, SongId, RemovalUiModel.Event) -> Unit = { date, songId, event ->
    val key = removalKey(date, songId)
    when (event) {
        RemovalUiModel.Event.RequestRemove -> updateConfirmation(key)

        RemovalUiModel.Event.Cancel -> if (isConfirming(key)) updateConfirmation(null)

        RemovalUiModel.Event.Confirm -> if (isConfirming(key)) {
            updateConfirmation(null)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { setlist.removeSong(date, songId) }
        }
    }
}
