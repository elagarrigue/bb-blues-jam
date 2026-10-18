package com.bbbjam.feature.nextjam.admin

import com.bbbjam.core.data.setlist.add.SetlistSongAddRepository
import com.bbbjam.core.data.setlist.move.SetlistMoveRepository
import com.bbbjam.core.data.setlist.remove.SetlistSongRemoveRepository

/**
 * The narrow contracts of the list's own mutations (add, remove, move), grouped so
 * `NextJamPresenter` stays under detekt's parameter limit (`setlist-mutation-contracts` D5). Never
 * `SetlistRepository`; `:app` binds each contract to the one instance.
 */
class NextJamSetlistWrites(
    val adds: SetlistSongAddRepository,
    val removals: SetlistSongRemoveRepository,
    val moves: SetlistMoveRepository,
)
