package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.admin.LoginOutcome
import com.bbbjam.core.data.setlist.AddSongOutcome
import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.data.setlist.RemoveSongOutcome
import com.bbbjam.core.data.setlist.SetKeyOutcome
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.data.setlist.SetlistRemove
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** An admin flag the test sets; it never stores or sends anything. Musician (false) by default. */
class FakeAdminSession(isAdmin: Boolean = false) : AdminSession {
    val isAdmin = MutableStateFlow(isAdmin)

    override fun observeIsAdmin(): Flow<Boolean> = isAdmin

    override suspend fun logIn(passphrase: String): LoginOutcome = LoginOutcome.Unavailable

    override suspend fun logOut() {
        isAdmin.value = false
    }
}

/**
 * Records every add, remove and dismiss; [adds] and [removes] are what `observeAdds` and
 * `observeRemoves` emit, set by the test.
 */
class FakeSetlistRepository : SetlistRepository {
    data class AddCall(val jamDate: LocalDate, val songId: SongId, val key: Key)

    val adds = MutableStateFlow<List<SetlistAdd>>(emptyList())
    val addCalls = mutableListOf<AddCall>()
    val dismissed = mutableListOf<Long>()
    val removes = MutableStateFlow<List<SetlistRemove>>(emptyList())
    val removeCalls = mutableListOf<Pair<LocalDate, SongId>>()
    val keyChanges = MutableStateFlow<List<KeyChange>>(emptyList())
    val keyCalls = mutableListOf<AddCall>()

    override suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome {
        addCalls += AddCall(jamDate, songId, key)
        return AddSongOutcome.Added(addCalls.size)
    }

    override fun observeAdds(): Flow<List<SetlistAdd>> = adds

    override suspend fun removeSong(jamDate: LocalDate, songId: SongId): RemoveSongOutcome {
        removeCalls += jamDate to songId
        return RemoveSongOutcome.Removed
    }

    override fun observeRemoves(): Flow<List<SetlistRemove>> = removes

    override suspend fun setKey(jamDate: LocalDate, songId: SongId, key: Key): SetKeyOutcome {
        keyCalls += AddCall(jamDate, songId, key)
        return SetKeyOutcome.KeySet
    }

    override fun observeKeyChanges(): Flow<List<KeyChange>> = keyChanges

    override fun dismiss(id: Long) {
        dismissed += id
    }
}
