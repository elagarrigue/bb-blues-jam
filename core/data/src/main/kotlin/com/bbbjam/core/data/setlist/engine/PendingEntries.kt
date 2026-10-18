package com.bbbjam.core.data.setlist.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The in-memory entries of one mutation: sending, or failed until dismissed. [idOf] reads an
 * entry's process-unique id; [canDismiss] says which entries [dismiss] may remove (a sending entry
 * never). The holder never touches the cache: a manager mirrors first and then calls [remove].
 */
internal class PendingEntries<E : Any>(private val idOf: (E) -> Long, private val canDismiss: (E) -> Boolean) {
    private val entries = MutableStateFlow<List<E>>(emptyList())

    fun observe(): StateFlow<List<E>> = entries.asStateFlow()

    fun add(entry: E) {
        entries.update { it + entry }
    }

    /** The write was confirmed: drops the entry [id]. */
    fun remove(id: Long) {
        entries.update { list -> list.filterNot { idOf(it) == id } }
    }

    /** The write failed: replaces the entry [id] with `transform(entry)`. */
    fun fail(id: Long, transform: (E) -> E) {
        entries.update { list -> list.map { if (idOf(it) == id) transform(it) else it } }
    }

    /** Removes the entry [id] if [canDismiss] allows it; otherwise, or for an unknown id, nothing. */
    fun dismiss(id: Long) {
        entries.update { list -> list.filterNot { idOf(it) == id && canDismiss(it) } }
    }
}
