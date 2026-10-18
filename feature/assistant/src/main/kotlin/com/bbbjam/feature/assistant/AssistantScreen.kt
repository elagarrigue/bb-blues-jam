package com.bbbjam.feature.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.bbbjam.core.ui.state.EmptyStateBlock
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.assistant.chat.StatusBanner
import com.bbbjam.feature.assistant.chat.Turn
import org.koin.compose.koinInject

/**
 * The Asistente tab (`assistant-admin-tab`): the title, the turns of the chat and the composer pinned
 * at the bottom above the keyboard. It draws [AssistantUiModel] and forwards events; the presenter
 * decides. Nothing here writes anywhere (D-24). The status-bar inset is [AppNavHost]'s.
 */
@Composable
fun AssistantScreen(modifier: Modifier = Modifier) {
    AssistantScreen(
        modifier = modifier,
        presenter = koinInject(),
    )
}

@Composable
internal fun AssistantScreen(modifier: Modifier = Modifier, presenter: AssistantPresenter) {
    AssistantContent(model = presenter.present(Unit), modifier = modifier)
}

@Composable
internal fun AssistantContent(model: AssistantUiModel, modifier: Modifier = Modifier) {
    val spacing = BluesJamTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BluesJamTheme.colors.background)
            .imePadding(),
    ) {
        Text(
            text = model.title,
            style = BluesJamTheme.typography.h1,
            color = BluesJamTheme.colors.text,
            modifier = Modifier
                .padding(horizontal = spacing.md, vertical = spacing.sm)
                .semantics { heading() },
        )
        when (model) {
            is AssistantUiModel.NotConfigured -> EmptyStateBlock(
                model.block,
                Modifier.padding(horizontal = spacing.md),
            )

            is AssistantUiModel.Chat -> {
                (model.status as? StatusUiModel.Banner)?.let { banner ->
                    StatusBanner(banner, Modifier.padding(horizontal = spacing.md, vertical = spacing.sm))
                }
                TurnList(model, Modifier.weight(1f))
                Composer(model.composer, model.status, Modifier.padding(spacing.md))
            }
        }
    }
}

@Composable
private fun TurnList(model: AssistantUiModel.Chat, modifier: Modifier) {
    val spacing = BluesJamTheme.spacing
    val listState = rememberLazyListState()
    val lastReply = model.turns.lastOrNull()?.reply
    // Scroll to the newest turn when one is added or its state changes.
    LaunchedEffect(model.turns.size, lastReply?.let { it::class }) {
        if (model.turns.isNotEmpty()) listState.animateScrollToItem(model.turns.lastIndex)
    }
    // While songs stream in, keep the end in view, but only if the admin had not scrolled up. The value is
    // read in this composition, before the new row is measured, so it is the state before the change.
    val songCount = (lastReply as? ReplyUiModel.Pending)?.songs?.size ?: 0
    val wasAtEnd = !listState.canScrollForward
    val previousCount = remember { intArrayOf(songCount) }
    val grew = songCount > previousCount[0]
    LaunchedEffect(songCount) {
        previousCount[0] = songCount
        if (shouldFollow(wasAtEnd, grew)) listState.animateScrollBy(FOLLOW_SCROLL_PX)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        model.intro?.let { intro -> item(key = INTRO_KEY) { EmptyStateBlock(intro) } }
        items(model.turns, key = { it.id }) { turn -> Turn(turn) }
    }
}

private const val INTRO_KEY = "intro"

/** Larger than any row: the scroll stops at the end of the list. */
private const val FOLLOW_SCROLL_PX = 10_000f

/** Follow a growing streamed list only when it was already at the end (the admin had not scrolled up). */
internal fun shouldFollow(wasAtEnd: Boolean, grew: Boolean): Boolean = wasAtEnd && grew

@Composable
private fun Composer(model: ComposerUiModel, status: StatusUiModel, modifier: Modifier) {
    val spacing = BluesJamTheme.spacing
    val field = AssistantDefaults.field()
    val focusManager = LocalFocusManager.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        (status as? StatusUiModel.Checking)?.let { checking ->
            // Static, no spinner, one polite live region (as the pending card).
            Text(
                text = checking.text,
                style = BluesJamTheme.typography.caption,
                color = AssistantDefaults.banner().checking,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        OutlinedTextField(
            value = model.text,
            onValueChange = { model.events(ComposerUiModel.Event.TextChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = BluesJamTheme.typography.body,
            label = { Text(text = model.label) },
            placeholder = { Text(text = model.placeholder) },
            maxLines = COMPOSER_MAX_LINES,
            shape = BluesJamTheme.shapes.md,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = field.text,
                unfocusedTextColor = field.text,
                focusedContainerColor = field.container,
                unfocusedContainerColor = field.container,
                cursorColor = field.cursor,
                focusedBorderColor = field.focusedBorder,
                unfocusedBorderColor = field.unfocusedBorder,
                focusedLabelColor = field.label,
                unfocusedLabelColor = field.label,
                focusedPlaceholderColor = field.label,
                unfocusedPlaceholderColor = field.label,
            ),
        )
        SendButton(model, focusManager)
    }
}

private const val COMPOSER_MAX_LINES = 5

/** Sending clears the field's focus, which hides the keyboard so the pending card is visible. */
@Composable
private fun SendButton(model: ComposerUiModel, focusManager: FocusManager) {
    val shape = BluesJamTheme.shapes.md
    val style = AssistantDefaults.sendButton(enabled = model.sendEnabled)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clip(shape)
            .background(style.fill, shape)
            .clickable(enabled = model.sendEnabled, role = Role.Button) {
                focusManager.clearFocus()
                model.events(ComposerUiModel.Event.Send)
            }
            .padding(horizontal = BluesJamTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = model.sendLabel, style = BluesJamTheme.typography.body, color = style.content)
    }
}
