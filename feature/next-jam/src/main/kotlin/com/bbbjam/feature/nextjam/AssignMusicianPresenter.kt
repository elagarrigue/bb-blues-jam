package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.Assignment
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.Presenter
import com.bbbjam.core.ui.state.EmptyStateUiModel
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Full-screen editor for one fixed open slot. */
class AssignMusicianPresenter(
    private val jams: JamsRepository,
    private val adminSession: AdminSession,
    private val setlist: SetlistRepository,
) : Presenter<AssignMusicianUiModel, AssignMusicianPresenter.Params> {
    data class Params(
        val jamDate: LocalDate,
        val songId: SongId,
        val instrument: Instrument,
        val ordinal: SlotPosition,
        val onBack: () -> Unit,
        val onDone: () -> Unit,
    )

    @Composable
    override fun present(params: Params): AssignMusicianUiModel {
        val currentDate by rememberUpdatedState(params.jamDate)
        val currentSong by rememberUpdatedState(params.songId)
        val currentInstrument by rememberUpdatedState(params.instrument)
        val currentOrdinal by rememberUpdatedState(params.ordinal)
        val currentBack by rememberUpdatedState(params.onBack)
        val currentDone by rememberUpdatedState(params.onDone)
        val scope = rememberCoroutineScope()
        val isAdmin by remember { adminSession.observeIsAdmin() }.collectAsState(initial = null)
        val snapshot by remember { jams.observeJams() }.collectAsState(initial = null)
        val assignments by remember { setlist.observeAssignments() }.collectAsState(initial = emptyList())
        var name by remember { mutableStateOf("") }
        var submitted by remember { mutableStateOf(false) }

        val submit: (String) -> Unit = { text ->
            val parsed = MusicianName.parseOrNull(text)
            if (!submitted && parsed != null) {
                submitted = true
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    setlist.assignSlot(currentDate, currentSong, currentInstrument, currentOrdinal, parsed)
                }
                currentDone()
            }
        }
        val back = backUiModel { currentBack() }
        val targetSong = snapshot?.let { assignableSong(isAdmin, it, assignments, params) }
        return when {
            isAdmin == null || snapshot == null -> AssignMusicianUiModel.Loading(back)
            targetSong == null -> gone(back)
            else -> buildContent(snapshot!!, params, name, back, targetSong, submit) { name = it }
        }
    }
}

private fun gone(back: BackUiModel) = AssignMusicianUiModel.Gone(
    back,
    EmptyStateUiModel(AssignMusicianCopy.GONE_TITLE, AssignMusicianCopy.GONE_MESSAGE),
)

private fun buildContent(
    snapshot: JamsSnapshot,
    params: AssignMusicianPresenter.Params,
    name: String,
    back: BackUiModel,
    targetSong: JamSong,
    submit: (String) -> Unit,
    onNameChanged: (String) -> Unit,
): AssignMusicianUiModel.Content {
    val validation = validationMessage(name)
    val suggestions = musicianSuggestions(snapshot, params.jamDate, name)
    return AssignMusicianUiModel.Content(
        back = back,
        title = AssignMusicianCopy.TITLE,
        songTitle = targetSong.title,
        slotLabel = AssignMusicianDefaults.slotName(params.instrument, params.ordinal.value),
        instrument = params.instrument,
        ordinal = params.ordinal.value,
        name = name,
        nameLabel = AssignMusicianCopy.NAME,
        validationMessage = validation,
        currentSection = suggestionSection(
            AssignMusicianCopy.CURRENT,
            suggestions.current,
            AssignMusicianCopy.CURRENT_EMPTY,
            submit,
        ),
        pastSection = suggestionSection(
            AssignMusicianCopy.PAST,
            suggestions.past,
            AssignMusicianCopy.PAST_EMPTY,
            submit,
        ),
        submitLabel = AssignMusicianCopy.SUBMIT,
        submitEnabled = MusicianName.parseOrNull(name) != null,
        events = EventHandler { event ->
            when (event) {
                is AssignMusicianUiModel.Content.Event.NameChanged -> onNameChanged(event.value)
                AssignMusicianUiModel.Content.Event.Submit -> submit(name)
            }
        },
    )
}

private fun upcomingSong(snapshot: JamsSnapshot?, date: LocalDate, id: SongId): JamSong? =
    snapshot?.upcoming?.takeIf { it.date == date }?.let { jam ->
        (jam.setlist as? Setlist.Available)?.songs?.firstOrNull { it.songId == id }
    }

internal fun assignableSong(
    isAdmin: Boolean?,
    snapshot: JamsSnapshot,
    assignments: List<Assignment>,
    params: AssignMusicianPresenter.Params,
): JamSong? {
    val allowed = isAdmin == true && !assignments.any { it.targets(params, requireSending = true) }
    val song = if (allowed) upcomingSong(snapshot, params.jamDate, params.songId) else null
    return song?.takeIf { it.lineup.slotAt(params.instrument, params.ordinal)?.isOpen == true }
}

private fun Assignment.targets(params: AssignMusicianPresenter.Params, requireSending: Boolean): Boolean =
    jamDate == params.jamDate &&
        songId == params.songId &&
        instrument == params.instrument &&
        ordinal == params.ordinal &&
        (!requireSending || state == Assignment.State.Sending)

internal data class MusicianSuggestions(val current: List<String>, val past: List<String>)

/** Suggestions include lineup names and Extras; prior jams are newest-first and exclude current names. */
internal fun musicianSuggestions(snapshot: JamsSnapshot?, date: LocalDate, query: String): MusicianSuggestions {
    val upcoming = snapshot?.upcoming?.takeIf { it.date == date }
    val currentRaw = upcoming?.let { jam ->
        (jam.setlist as? Setlist.Available)?.songs.orEmpty().flatMap(JamSong::participantNames)
    }.orEmpty()
    val current = distinctNames(currentRaw).filter { it.matchesWords(query) }.take(MAX_SUGGESTIONS)
    val currentKeys = currentRaw.map(String::nameKey).toSet()
    val latestAppearance = linkedMapOf<String, Pair<LocalDate, String>>()
    snapshot?.past.orEmpty().sortedByDescending { it.date }.forEach { jam ->
        (jam.setlist as? Setlist.Available)?.songs.orEmpty().flatMap(JamSong::participantNames).forEach { name ->
            val key = name.nameKey()
            if (key !in currentKeys) latestAppearance.putIfAbsent(key, jam.date to name.trim())
        }
    }
    val past = latestAppearance.values
        .sortedWith(compareByDescending<Pair<LocalDate, String>> { it.first }.thenBy { it.second.nameKey() })
        .map { it.second }
        .filter { it.matchesWords(query) }
        .take(MAX_SUGGESTIONS)
    return MusicianSuggestions(current, past)
}

private fun JamSong.participantNames(): List<String> =
    lineup.slots.mapNotNull { it.musicianName } + extraParticipants.map(ExtraParticipant::name)

private fun distinctNames(names: List<String>): List<String> {
    val chosen = linkedMapOf<String, String>()
    names.forEach { name -> chosen.putIfAbsent(name.nameKey(), name.trim()) }
    return chosen.values.toList()
}

private fun String.matchesWords(query: String): Boolean {
    val words = query.nameKey().split(WHITESPACE).filter(String::isNotEmpty)
    return words.isEmpty() ||
        nameKey().split(WHITESPACE).filter(String::isNotEmpty).any { word -> words.any(word::startsWith) }
}

private fun String.nameKey(): String =
    MARKS.replace(Normalizer.normalize(trim(), Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

private fun suggestionSection(label: String, names: List<String>, empty: String, submit: (String) -> Unit) =
    SuggestionSectionUiModel(
        label = label,
        emptyMessage = empty.takeIf { names.isEmpty() },
        rows = names.map { name ->
            SuggestionRowUiModel(
                name,
                "anotar $name",
                EventHandler(key = name.nameKey()) { event ->
                    when (event) {
                        SuggestionRowUiModel.Event.Select -> submit(name)
                    }
                },
            )
        },
    )

private const val MAX_SUGGESTIONS = 8
private val MARKS = Regex("\\p{Mn}+")
private val WHITESPACE = Regex("\\s+")
