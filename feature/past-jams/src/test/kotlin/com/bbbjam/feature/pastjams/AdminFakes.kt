package com.bbbjam.feature.pastjams

import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.admin.LoginOutcome
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.setlist.CreateJamOutcome
import com.bbbjam.core.data.setlist.DeleteJamOutcome
import com.bbbjam.core.data.setlist.JamCreator
import com.bbbjam.core.data.setlist.JamEdit
import com.bbbjam.core.data.setlist.JamStatusChange
import com.bbbjam.core.data.setlist.PublishOutcome
import com.bbbjam.core.data.setlist.SetJamStatusOutcome
import com.bbbjam.core.data.setlist.SetlistJamEditRepository
import com.bbbjam.core.data.setlist.SetlistJamStatusRepository
import com.bbbjam.core.data.setlist.SetlistPublish
import com.bbbjam.core.data.setlist.SetlistPublishRepository
import com.bbbjam.core.data.setlist.UpdateJamOutcome
import com.bbbjam.core.model.JamStatus
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
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

/** Records every status change and dismissal; [changes] is what `observeJamStatusChanges` emits. */
class FakeJamStatusRepository : SetlistJamStatusRepository {
    val changes = MutableStateFlow<List<JamStatusChange>>(emptyList())
    val calls = mutableListOf<Pair<LocalDate, JamStatus>>()
    val dismissed = mutableListOf<Long>()
    var hold: CompletableDeferred<Unit>? = null

    override suspend fun setJamStatus(jamDate: LocalDate, status: JamStatus): SetJamStatusOutcome {
        calls += jamDate to status
        hold?.await()
        return SetJamStatusOutcome.StatusSet(status, changed = true)
    }

    override fun observeJamStatusChanges(): Flow<List<JamStatusChange>> = changes

    override fun dismissJamStatusChange(id: Long) {
        dismissed += id
        changes.value = changes.value.filterNot { it.id == id && it.state !is JamStatusChange.State.Sending }
    }
}

/** Records every publication; a [hold] keeps one `Sending` until the test completes it. */
class FakePublishRepository : SetlistPublishRepository {
    val entries = MutableStateFlow<List<SetlistPublish>>(emptyList())
    val calls = mutableListOf<LocalDate>()
    val dismissed = mutableListOf<Long>()
    var hold: CompletableDeferred<Unit>? = null

    override suspend fun publishSetlist(jamDate: LocalDate): PublishOutcome {
        calls += jamDate
        entries.value = entries.value + SetlistPublish(900, jamDate, SetlistPublish.State.Sending)
        hold?.await()
        entries.value = entries.value.filterNot { it.id == 900L }
        return PublishOutcome.Published(alreadyPublished = false)
    }

    override fun observePublishes(): Flow<List<SetlistPublish>> = entries

    override fun dismissPublish(id: Long) {
        dismissed += id
        entries.value = entries.value.filterNot { it.id == id && it.state is SetlistPublish.State.Failed }
    }
}

/** Records every creation and answers [outcome]; a [hold] keeps the call open until the test completes it. */
class FakeJamCreator : JamCreator {
    data class Call(val date: LocalDate, val time: LocalTime, val venue: String)

    val calls = mutableListOf<Call>()
    var outcome: CreateJamOutcome? = null
    var hold: CompletableDeferred<Unit>? = null

    override suspend fun createJam(date: LocalDate, startTime: LocalTime, venue: String): CreateJamOutcome {
        calls += Call(date, startTime, venue)
        hold?.await()
        return outcome ?: CreateJamOutcome.Created(date, startTime, venue)
    }
}

/** A refusal outcome for tests. */
fun notCreated(code: String): CreateJamOutcome = CreateJamOutcome.NotCreated(WriteOutcome.Rejected(code))

/** Records every edit and deletion; [entries] is what `observeJamEdits` emits, a [hold] keeps a call open. */
class FakeJamEditRepository : SetlistJamEditRepository {
    data class Update(val jamDate: LocalDate, val newDate: LocalDate, val time: LocalTime, val venue: String)

    val entries = MutableStateFlow<List<JamEdit>>(emptyList())
    val updates = mutableListOf<Update>()
    val deletes = mutableListOf<Pair<LocalDate, Int>>()
    val dismissed = mutableListOf<Long>()
    var hold: CompletableDeferred<Unit>? = null

    override suspend fun updateJam(
        jamDate: LocalDate,
        newDate: LocalDate,
        startTime: LocalTime,
        venue: String,
    ): UpdateJamOutcome {
        updates += Update(jamDate, newDate, startTime, venue)
        hold?.await()
        return UpdateJamOutcome.Updated(newDate, startTime, venue, changed = true)
    }

    override suspend fun deleteJam(jamDate: LocalDate, expectedSongs: Int): DeleteJamOutcome {
        deletes += jamDate to expectedSongs
        hold?.await()
        return DeleteJamOutcome.Deleted(expectedSongs)
    }

    override fun observeJamEdits(): Flow<List<JamEdit>> = entries

    override fun dismissJamEdit(id: Long) {
        dismissed += id
        entries.value = entries.value.filterNot { it.id == id && it.state !is JamEdit.State.Sending }
    }
}
