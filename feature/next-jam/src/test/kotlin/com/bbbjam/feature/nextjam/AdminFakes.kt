package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.admin.LoginOutcome
import com.bbbjam.core.data.setlist.AddSongOutcome
import com.bbbjam.core.data.setlist.SetlistAdd
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

/** Records every add and dismiss; [adds] is what `observeAdds` emits, set by the test. */
class FakeSetlistRepository : SetlistRepository {
    data class AddCall(val jamDate: LocalDate, val songId: SongId, val key: Key)

    val adds = MutableStateFlow<List<SetlistAdd>>(emptyList())
    val addCalls = mutableListOf<AddCall>()
    val dismissed = mutableListOf<Long>()

    override suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome {
        addCalls += AddCall(jamDate, songId, key)
        return AddSongOutcome.Added(addCalls.size)
    }

    override fun observeAdds(): Flow<List<SetlistAdd>> = adds

    override fun dismiss(id: Long) {
        dismissed += id
    }
}
