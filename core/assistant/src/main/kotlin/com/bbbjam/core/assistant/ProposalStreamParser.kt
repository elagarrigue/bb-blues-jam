package com.bbbjam.core.assistant

/**
 * Finds the `songs` objects of the model's JSON answer while it streams (`assistant-streaming`). Feed it
 * the text deltas in order; each call returns the raw text of every element object of the top-level
 * `songs` array that the chunk completed. A character state machine: chunk boundaries may fall anywhere,
 * inside a key, a string, an escape or a multi-unit character. Text before the first `{` (a code fence,
 * a sentence) is ignored. Objects under any other key, and anything after the `songs` array, are never
 * returned. It decodes nothing and never throws: a malformed element is returned as raw text and the
 * caller drops it when decoding fails. Pure; one instance per answer, [reset] between rounds.
 */
internal class ProposalStreamParser {
    private var depth = 0
    private var inString = false
    private var escaped = false
    private var readingKey = false
    private var expectKey = false
    private var pendingKey: String? = null
    private val key = StringBuilder()
    private var inSongs = false
    private var songsDone = false
    private var element: StringBuilder? = null

    /** Consumes [chunk] and returns the song objects it completed, in order. */
    fun feed(chunk: String): List<String> = chunk.mapNotNull(::step)

    /** Forgets everything: the next [feed] starts a new answer. */
    fun reset() {
        depth = 0
        inString = false
        escaped = false
        readingKey = false
        expectKey = false
        pendingKey = null
        key.clear()
        inSongs = false
        songsDone = false
        element = null
    }

    private fun step(c: Char): String? {
        element?.append(c)
        return when {
            depth == 0 && c != '{' -> null
            inString -> inStringChar(c)
            else -> structural(c)
        }
    }

    private fun structural(c: Char): String? = when (c) {
        '"' -> startString()
        '{', '[' -> openContainer(c)
        '}', ']' -> closeContainer(c)
        ':', ',' -> separator(c)
        else -> null
    }

    private fun startString(): String? {
        inString = true
        readingKey = depth == 1 && expectKey
        if (readingKey) key.clear()
        return null
    }

    private fun inStringChar(c: Char): String? {
        val closes = c == '"' && !escaped
        escaped = c == BACKSLASH && !escaped
        if (closes) {
            inString = false
            if (readingKey) pendingKey = key.toString()
            readingKey = false
        } else if (readingKey && key.length < MAX_KEY_LENGTH) {
            key.append(c)
        }
        return null
    }

    private fun openContainer(c: Char): String? {
        if (c == '{') openObject() else openArray()
        depth++
        if (depth == 1) expectKey = true
        return null
    }

    private fun openObject() {
        if (inSongs && depth == SONGS_DEPTH && element == null) element = StringBuilder("{")
    }

    private fun openArray() {
        val isSongsKey = depth == 1 && pendingKey == SONGS_KEY
        if (isSongsKey && !songsDone && !inSongs) inSongs = true
    }

    private fun closeContainer(c: Char): String? {
        depth--
        if (c == ']') {
            if (inSongs && depth == 1) {
                inSongs = false
                songsDone = true
            }
            return null
        }
        val finished = element?.takeIf { depth == SONGS_DEPTH }?.toString()
        if (finished != null) element = null
        return finished
    }

    private fun separator(c: Char): String? {
        if (depth == 1) {
            expectKey = c == ','
            if (expectKey) pendingKey = null
        }
        return null
    }

    private companion object {
        const val SONGS_KEY = "songs"
        const val BACKSLASH = '\\'

        /** Inside the top-level object (1) and the `songs` array (2): an element opens at this depth. */
        const val SONGS_DEPTH = 2
        const val MAX_KEY_LENGTH = 32
    }
}
