package com.bbbjam.core.ui.presenter

import androidx.compose.runtime.Immutable

/**
 * Wraps an event lambda so a UiModel stays comparable. Two handlers are equal when their keys are
 * equal, and `hashCode` derives from the same key, which lets tests compare a whole UiModel
 * against one built with `EventHandler {}`.
 * Give a key only when a handler is the sole changing property of a model.
 */
@Immutable
class EventHandler<E : UiEvent>(private val key: Any? = null, val handle: (E) -> Unit) {
    operator fun invoke(event: E) = handle(event)

    override fun equals(other: Any?): Boolean = other is EventHandler<*> && key == other.key

    override fun hashCode(): Int = key?.hashCode() ?: 0

    override fun toString(): String = "EventHandler(key=$key)"
}
