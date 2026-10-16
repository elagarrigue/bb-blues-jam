package com.bbbjam.feature.nextjam

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.lineup.LineupPanel
import com.bbbjam.core.ui.nav.BackButton
import com.bbbjam.core.ui.state.EmptyStateBlock
import com.bbbjam.core.ui.theme.BluesJamTheme
import java.time.LocalDate
import java.time.LocalTime
import org.koin.compose.koinInject

/**
 * The song detail: a back button, the title, the artist when there is one, the key very large in
 * the amber `key` role (the screen's main element, read from arm's length) and the lineup panel
 * (open slots first, then filled, then `Otros`). Musicians only read it. For the admin
 * (`song-detail-replaces-row-expansion`) each block gains its controls after what a musician sees:
 * "Guardando…" under the title, the key opens its picker on tap via [onSetKey], "Anotar",
 * "Liberar" and "Quitar" on the panel lines ("Anotar" calls [onAssignSlot]), "Cambiar formación" and
 * a "+ Otro / Agregar" row after the open slots, then the song's failure cards. It renders
 * [SongDetailUiModel] and forwards events; the presenter decides. [onBack], [onSetKey] and
 * [onAssignSlot] are the caller's: `:app` binds them to navigation, which this module never sees.
 * [contentPadding] goes inside the list, so the background runs edge to edge.
 */
@Composable
fun SongDetailScreen(
    jamDate: LocalDate,
    songId: SongId,
    position: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onSetKey: (jamDate: LocalDate, songId: SongId) -> Unit = { _, _ -> },
    onAssignSlot: (
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        position: SlotPosition,
    ) -> Unit = { _, _, _, _ -> },
    presenter: SongDetailPresenter = koinInject(),
) {
    val model = presenter.present(
        SongDetailPresenter.Params(jamDate, songId, position, onBack, onSetKey, onAssignSlot),
    )
    SongDetailContent(model = model, modifier = modifier, contentPadding = contentPadding)
}

@Composable
internal fun SongDetailContent(model: SongDetailUiModel, modifier: Modifier, contentPadding: PaddingValues) {
    val spacing = BluesJamTheme.spacing
    val layoutDirection = LocalLayoutDirection.current
    // The back button brings its own 48dp target, so the top and start padding are the small step.
    val padding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) + spacing.sm,
        top = contentPadding.calculateTopPadding() + spacing.sm,
        end = contentPadding.calculateEndPadding(layoutDirection) + spacing.md,
        bottom = contentPadding.calculateBottomPadding() + spacing.lg,
    )
    // The "Agregar a Otros" dialog is a sibling of the list, not an item in it: it floats over the
    // whole screen and survives the list scrolling (`extras-dialog-and-lines`).
    (model as? SongDetailUiModel.Song)?.admin?.extraParticipants?.takeIf { it.formVisible }?.let {
        AddExtraDialog(it)
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BluesJamTheme.colors.background)
            .semantics { if (model is SongDetailUiModel.Loading) contentDescription = model.description },
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item(key = BACK_KEY) { BackButton(model.back) }
        when (model) {
            is SongDetailUiModel.Loading -> Unit

            is SongDetailUiModel.NotFound -> item(key = NOT_FOUND_KEY) {
                EmptyStateBlock(model.empty, Modifier.padding(start = spacing.sm))
            }

            is SongDetailUiModel.Song -> songItems(model)
        }
    }
}

private fun LazyListScope.songItems(model: SongDetailUiModel.Song) {
    val admin = model.admin
    item(key = TITLE_KEY) { Title(model, Modifier.padding(start = BluesJamTheme.spacing.sm)) }
    item(key = KEY_KEY) {
        val keyAction = admin?.setKey
        KeyBlock(
            model = model,
            modifier = Modifier.padding(start = BluesJamTheme.spacing.sm),
            onClick = keyAction?.let { { it.events(SetKeyActionUiModel.Event.Open) } },
            clickLabel = keyAction?.label,
        )
    }
    item(key = LINEUP_KEY) {
        Surface(
            color = BluesJamTheme.colors.surface,
            contentColor = BluesJamTheme.colors.text,
            shape = BluesJamTheme.shapes.md,
            modifier = Modifier
                .padding(start = BluesJamTheme.spacing.sm)
                .fillMaxWidth(),
        ) {
            Column {
                LineupPanel(
                    model = model.lineup,
                    modifier = Modifier.padding(BluesJamTheme.spacing.md),
                    openSlotsFooter = admin?.let { { ExtraParticipantEditor(it.extraParticipants) } },
                )
                admin?.let {
                    LineupEditor(it.lineup)
                }
            }
        }
    }
    admin?.let {
        items(it.failures, key = { failure -> "failure-${failure.id}" }) { failure ->
            Box(Modifier.padding(start = BluesJamTheme.spacing.sm)) { AddFailureCard(failure) }
        }
    }
}

private const val BACK_KEY = "back"
private const val NOT_FOUND_KEY = "notFound"
private const val TITLE_KEY = "title"
private const val KEY_KEY = "key"
private const val LINEUP_KEY = "lineup"

@Composable
private fun Title(model: SongDetailUiModel.Song, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = model.title,
            style = SongDetailDefaults.titleStyle,
            color = BluesJamTheme.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        model.artist?.let { artist ->
            Text(text = artist, style = SongDetailDefaults.bodyStyle, color = BluesJamTheme.colors.textMuted)
        }
        model.admin?.saveStatus?.let { status -> SaveStatusLine(status) }
    }
}

/** The label and key are one screen-reader node; for the admin it also opens the picker on tap. */
@Composable
private fun KeyBlock(
    model: SongDetailUiModel.Song,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
) {
    val keyModifier = if (onClick != null) {
        Modifier
            .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = model.keyDescription }
    } else {
        Modifier.clearAndSetSemantics { contentDescription = model.keyDescription }
    }
    Column(modifier = modifier.then(keyModifier)) {
        Text(
            text = model.keyLabel.uppercase(),
            style = SongDetailDefaults.captionStyle,
            color = BluesJamTheme.colors.textMuted,
        )
        Text(
            text = model.key,
            style = SongDetailDefaults.keyStyle,
            color = SongDetailDefaults.keyColor(BluesJamTheme.colors),
            maxLines = 1,
        )
    }
}

private val previewDate: LocalDate = LocalDate.of(2026, 10, 31)

/** The preview's model, built through the real mapping from a jam that holds [song]; [admin] for the admin's. */
private fun previewModel(song: JamSong, admin: SongAdminState? = null): SongDetailUiModel {
    val jam = Jam(previewDate, LocalTime.of(21, 0), "La Macanuda", JamStatus.PUBLISHED, Setlist.Available(listOf(song)))
    return JamsSnapshot(jam, emptyList(), Freshness(null, null, isRefreshing = false))
        .toSongDetail(previewDate, song.songId, song.position, onBack = {}, admin = admin)
}

@Composable
private fun DetailPreview(model: SongDetailUiModel) {
    BluesJamTheme { SongDetailContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) }
}

/** Only the required fields: blank artist, no extras, the default lineup. */
@Preview
@Composable
private fun RequiredFieldsPreview() = DetailPreview(
    previewModel(
        JamSong(
            position = 1,
            songId = SongId("sweet-little-angel"),
            title = "Sweet Little Angel",
            artist = "",
            key = Key("B"),
            lineup = Lineup.default(),
        ),
    ),
)

private val fullSong = JamSong(
    position = 5,
    songId = SongId("the-thrill-is-gone"),
    title = "The Thrill Is Gone",
    artist = "B.B. King",
    key = Key("Bbm"),
    lineup = Lineup(
        listOf(
            Slot(Instrument.GUITAR, "Tincho"),
            Slot(Instrument.GUITAR),
            Slot(Instrument.BASS, "Nico"),
            Slot(Instrument.DRUMS),
            Slot(Instrument.VOCALS, "Laura"),
            Slot(Instrument.HARMONICA, "Mono"),
        ),
    ),
    extraParticipants = listOf(ExtraParticipant("Juan", "saxo")),
)

@Preview
@Composable
private fun FullSongPreview() = DetailPreview(previewModel(fullSong))

/** The admin's detail: the same song with "Cambiar tonalidad", the line actions and the editors. */
@Preview(heightDp = PREVIEW_TALL)
@Composable
private fun AdminSongPreview() = DetailPreview(previewModel(fullSong, SongAdminState()))

private const val PREVIEW_TALL = 1400

@Preview
@Composable
private fun NotFoundPreview() = DetailPreview(
    JamsSnapshot(
        null,
        emptyList(),
        Freshness(null, null, isRefreshing = false),
    ).toSongDetail(previewDate, SongId("sweet-little-angel"), 1, onBack = {}),
)
