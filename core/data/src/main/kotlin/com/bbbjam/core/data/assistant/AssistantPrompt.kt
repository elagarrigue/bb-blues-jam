package com.bbbjam.core.data.assistant

import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt

/** The system prompt and the conversation history the agent starts each turn from. */
internal object AssistantPrompt {
    /** Answered turns kept in the prompt; older ones are dropped. */
    const val MAX_TURNS = 6

    /** One answered turn: the admin's text and the validated proposal re-encoded as the model's JSON. */
    data class Turn(val request: String, val answer: String)

    val SYSTEM: String = """
        You are the setlist assistant of a monthly blues jam. The admin describes the jam they want and
        you propose an ordered setlist taken ONLY from the band's song catalog.
        Rules:
        - Use the tools. Call searchCatalog to see which songs exist (an empty query lists the whole
          catalog) and pastJams to see what was played in past jams and who played it.
        - Never invent a song. Every songId must be copied exactly from searchCatalog. Do not propose a
          song twice.
        - Copy title, artist and key from the catalog.
        - Write message, questions and every reason in Rioplatense Spanish using vos (never tu).
        - Keep each reason to one short sentence about why the song fits that place in the list.
        - When the request is ambiguous, ask in questions; songs may then be empty or a first draft.
        - When the admin asks to change the previous proposal, return the complete updated list.
        - The final answer must be only the JSON object requested, with no other text.
    """.trimIndent()

    /** The prompt for a new turn: the system message, then [history] as user and assistant messages. */
    fun build(history: List<Turn>): Prompt = prompt("setlist-assistant") {
        system(SYSTEM)
        history.takeLast(MAX_TURNS).forEach { turn ->
            user(turn.request)
            assistant(turn.answer)
        }
    }
}
