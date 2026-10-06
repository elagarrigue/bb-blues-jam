package com.bbbjam.feature.nextjam

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.nav.BackButton
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateBlock
import com.bbbjam.core.ui.state.ListErrorBlock
import com.bbbjam.core.ui.state.SkeletonList
import com.bbbjam.core.ui.theme.BluesJamTheme
import java.time.LocalDate
import org.koin.compose.koinInject

/**
 * The catalog picker (`admin-add-song-to-setlist`, V1): full screen over the tabs, a back button,
 * the title, an outlined search field and the catalog sorted by title, each row with its artist and
 * the key it will be added in. A song already in the list is muted with "Ya está en la lista" and
 * not clickable. Tapping a song adds it and calls [onAdded] at once (`:app` closes the screen). It
 * draws [AddSongUiModel] and forwards events; the presenter decides. The content sits above the
 * keyboard (`imePadding`).
 */
@Composable
fun AddSongScreen(
    jamDate: LocalDate,
    onBack: () -> Unit,
    onAdded: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    presenter: AddSongPresenter = koinInject(),
) {
    val model = presenter.present(AddSongPresenter.Params(jamDate = jamDate, onBack = onBack, onAdded = onAdded))
    AddSongContent(model = model, modifier = modifier, contentPadding = contentPadding)
}

@Composable
private fun AddSongContent(model: AddSongUiModel, modifier: Modifier, contentPadding: PaddingValues) {
    val spacing = BluesJamTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BluesJamTheme.colors.background)
            .imePadding()
            .padding(horizontal = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        BackButton(model.back, modifier = Modifier.padding(top = spacing.sm))
        Text(
            text = model.title,
            style = BluesJamTheme.typography.h1,
            color = BluesJamTheme.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        SearchField(model.search)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                top = spacing.sm,
                bottom =
                    contentPadding.calculateBottomPadding() + spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            when (val content = model.content) {
                is AddSongContentUiModel.Loading -> item(key = STATE_KEY) { SkeletonList(content.description) }

                is AddSongContentUiModel.Failed -> item(key = STATE_KEY) { ListErrorBlock(content.error) }

                is AddSongContentUiModel.Empty -> item(key = STATE_KEY) { EmptyStateBlock(content.empty) }

                is AddSongContentUiModel.NoResults -> item(key = STATE_KEY) {
                    Text(
                        text = content.message,
                        style = BluesJamTheme.typography.body,
                        color = BluesJamTheme.colors.textMuted,
                    )
                }

                is AddSongContentUiModel.Songs -> items(content.rows, key = { it.id }) { row -> CatalogRow(row) }
            }
        }
    }
}

private const val STATE_KEY = "state"

@Composable
private fun SearchField(model: SearchFieldUiModel) {
    val style = AddSongDefaults.field()
    OutlinedTextField(
        value = model.query,
        onValueChange = { model.events(SearchFieldUiModel.Event.Changed(it)) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = BluesJamTheme.typography.body,
        label = { Text(text = model.label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = BluesJamTheme.shapes.md,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = style.text,
            unfocusedTextColor = style.text,
            focusedContainerColor = style.container,
            unfocusedContainerColor = style.container,
            cursorColor = style.cursor,
            focusedBorderColor = style.focusedBorder,
            unfocusedBorderColor = style.unfocusedBorder,
            focusedLabelColor = style.label,
            unfocusedLabelColor = style.label,
        ),
    )
}

/**
 * One catalog song, one merged node: title and key on the first line, the artist (and, for a song
 * already listed, "Ya está en la lista") under them. Clickable, with the click label "agregar a la
 * lista", only when the song is not listed.
 */
@Composable
private fun CatalogRow(row: CatalogRowUiModel) {
    val spacing = BluesJamTheme.spacing
    val style = AddSongDefaults.row(row.isListed)
    val click = if (row.isListed) {
        Modifier.semantics(mergeDescendants = true) {}
    } else {
        Modifier.clickable(onClickLabel = row.pickLabel, role = Role.Button) {
            row.events(CatalogRowUiModel.Event.Pick)
        }
    }
    Surface(color = style.container, shape = BluesJamTheme.shapes.md, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = click
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(
                    text = row.title,
                    style = BluesJamTheme.typography.songTitle,
                    color = style.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.key,
                    style = BluesJamTheme.typography.songTitle,
                    color = style.key,
                    modifier = Modifier.semantics { contentDescription = row.keyDescription },
                )
            }
            if (row.artist.isNotBlank()) {
                Text(text = row.artist, style = BluesJamTheme.typography.body, color = style.artist)
            }
            row.listedLabel?.let { Text(text = it, style = BluesJamTheme.typography.caption, color = style.note) }
        }
    }
}

@Preview
@Composable
private fun AddSongScreenPreview() {
    fun row(id: String, title: String, artist: String, key: String, listed: Boolean = false) = CatalogRowUiModel(
        id = id,
        title = title,
        artist = artist,
        key = key,
        keyDescription = NextJamCopy.keyDescription(key),
        isListed = listed,
        listedLabel = if (listed) AddSongCopy.ALREADY_LISTED else null,
        pickLabel = AddSongCopy.PICK_LABEL,
        events = EventHandler {},
    )
    val model = AddSongUiModel(
        back = backUiModel {},
        title = AddSongCopy.TITLE,
        search = SearchFieldUiModel("", AddSongCopy.SEARCH_LABEL, EventHandler {}),
        content = AddSongContentUiModel.Songs(
            listOf(
                row("crossroads", "Crossroads", "Eric Clapton", "A"),
                row("red-house", "Red House", "Jimi Hendrix", "Bb", listed = true),
                row("the-thrill-is-gone", "The Thrill Is Gone", "B.B. King", "Bm"),
            ),
        ),
    )
    BluesJamTheme { Box { AddSongContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) } }
}
