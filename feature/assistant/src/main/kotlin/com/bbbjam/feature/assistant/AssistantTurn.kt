package com.bbbjam.feature.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.bbbjam.core.ui.state.LoadingDots
import com.bbbjam.core.ui.theme.BluesJamTheme

/** One turn of the chat: the admin's request card and the reply card under it. */
@Composable
internal fun Turn(turn: TurnUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.sm)) {
        val style = AssistantDefaults.requestCard()
        Card(style) {
            Caption(turn.authorLabel, style.caption)
            Text(text = turn.request, style = BluesJamTheme.typography.body, color = style.text)
        }
        Reply(turn.reply)
    }
}

@Composable
private fun Card(style: AssistantDefaults.CardStyle, content: @Composable () -> Unit) {
    val spacing = BluesJamTheme.spacing
    Surface(color = style.fill, contentColor = style.text, shape = BluesJamTheme.shapes.md) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) { content() }
    }
}

@Composable
internal fun Caption(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text = text.uppercase(), style = BluesJamTheme.typography.caption, color = color, modifier = modifier)
}

@Composable
private fun Reply(reply: ReplyUiModel) {
    val card = AssistantDefaults.replyCard()
    val notice = AssistantDefaults.notice()
    Card(card) {
        when (reply) {
            is ReplyUiModel.Pending -> PendingContent(reply, notice)
            is ReplyUiModel.Proposal -> ProposalContent(reply, card)
            is ReplyUiModel.Incomplete -> IncompleteContent(reply, card, notice)
            is ReplyUiModel.Failed -> NoticeContent(reply.message, notice.failure, reply.retry, notice)
            is ReplyUiModel.Cancelled -> NoticeContent(reply.message, notice.cancelled, reply.retry, notice)
        }
    }
}

@Composable
private fun PendingContent(reply: ReplyUiModel.Pending, notice: AssistantDefaults.NoticeStyle) {
    // Songs that already arrived, then the only moving element in the app (DESIGN.md M1 exception): one
    // polite node announces the wait. The songs are plain nodes, not live regions.
    reply.songsHeading?.let { SongList(it, reply.songs, BluesJamTheme.colors.textMuted) }
    LoadingDots(description = reply.loadingLabel)
    TextAction(reply.cancelLabel, notice.action) { reply.events(ReplyUiModel.Pending.Event.Cancel) }
}

/** An answer that broke or was cancelled: the songs that arrived, marked incomplete, and "Reintentar". */
@Composable
private fun IncompleteContent(
    reply: ReplyUiModel.Incomplete,
    card: AssistantDefaults.CardStyle,
    notice: AssistantDefaults.NoticeStyle,
) {
    SongList(reply.heading, reply.songs, card.caption)
    val color = if (reply.isCancelled) notice.cancelled else notice.failure
    Text(text = reply.message, style = BluesJamTheme.typography.body, color = color)
    reply.retry?.let { TextAction(it.label, notice.action) { it.events(RetryUiModel.Event.Retry) } }
}

@Composable
private fun NoticeContent(message: String, color: Color, retry: RetryUiModel?, notice: AssistantDefaults.NoticeStyle) {
    Text(text = message, style = BluesJamTheme.typography.body, color = color)
    retry?.let { TextAction(it.label, notice.action) { it.events(RetryUiModel.Event.Retry) } }
}

@Composable
private fun ProposalContent(reply: ReplyUiModel.Proposal, card: AssistantDefaults.CardStyle) {
    val typography = BluesJamTheme.typography
    val spacing = BluesJamTheme.spacing
    Caption(reply.authorLabel, card.caption)
    reply.jam?.let { JamCard(it) }
    reply.jamNote?.let { Text(text = it, style = typography.body, color = JamDefaults.rejectionNote()) }
    reply.heading?.let { headingText ->
        SongList(headingText, reply.songs, card.caption)
    }
    Text(text = reply.message, style = typography.body, color = card.text)
    reply.questionsHeading?.let { headingText ->
        Caption(headingText, card.caption, Modifier.semantics { heading() })
        reply.questions.forEach { question ->
            Text(text = "• $question", style = typography.body, color = card.text)
        }
    }
    reply.discardedNote?.let { note ->
        Text(text = note, style = typography.caption, color = AssistantDefaults.notice().caption)
    }
    ApplyContent(reply.apply, reply.summary, reply.removals, reply.order)
}

/** An underlined text action with a 48dp target, in `text` (the primary action is "Enviar"). */
@Composable
internal fun TextAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clip(BluesJamTheme.shapes.md)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = BluesJamTheme.typography.body,
            color = color,
            textDecoration = TextDecoration.Underline,
        )
    }
}

/** The service banner: why the assistant cannot be used now, and "Reintentar" to ask again. Never amber. */
@Composable
internal fun StatusBanner(banner: StatusUiModel.Banner, modifier: Modifier = Modifier) {
    val style = AssistantDefaults.banner()
    Surface(color = style.fill, contentColor = style.text, shape = BluesJamTheme.shapes.md, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(BluesJamTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.sm),
        ) {
            Column(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(text = banner.message, style = BluesJamTheme.typography.body, color = style.text)
            }
            TextAction(banner.retryLabel, style.action) { banner.events(StatusUiModel.Banner.Event.Retry) }
        }
    }
}
