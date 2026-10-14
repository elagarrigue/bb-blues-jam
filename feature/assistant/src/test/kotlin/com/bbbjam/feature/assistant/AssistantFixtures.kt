package com.bbbjam.feature.assistant

import com.bbbjam.core.data.assistant.AssistantFailure
import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.assistant.ProposedSong
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.presenter.EventHandler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher

internal fun song(n: Int, key: String = "Bm") =
    ProposedSong(SongId("s$n"), "Tema $n", "Artista $n", Key(key), "Razón $n")

internal fun proposal(
    songs: Int = 2,
    message: String = "Listo.",
    questions: List<String> = emptyList(),
    rejected: List<String> = emptyList(),
) = AssistantReply.Proposal((1..songs).map { song(it) }, message, questions, rejected)

internal fun failed(failure: AssistantFailure) = AssistantReply.Failed(failure)

/** A chat scope on the test scheduler that records escaped exceptions instead of failing the test. */
internal class ChatScope(scheduler: TestCoroutineScheduler) {
    val escaped = mutableListOf<Throwable>()
    val scope = CoroutineScope(
        SupervisorJob() + UnconfinedTestDispatcher(scheduler) + CoroutineExceptionHandler { _, e -> escaped += e },
    )
}

internal fun testHandlers() = AssistantHandlers(
    cancel = { id -> EventHandler(id) {} },
    retry = { id -> EventHandler(id) {} },
    composer = EventHandler {},
    status = EventHandler {},
)
