package com.bbbjam.core.ui.presenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Guards the two defects of the source articles (bitácora 3.5): equals/hashCode and invoke. */
class EventHandlerTest {
    private sealed interface Event : UiEvent {
        data object Ping : Event

        data class Pick(val index: Int) : Event
    }

    @Test
    fun `unkeyed handlers with different lambdas are equal with equal hash codes`() {
        val a = EventHandler<Event> { }
        val b = EventHandler<Event> { error("never called") }

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `handlers with the same key are equal with equal hash codes`() {
        val a = EventHandler<Event>(key = "row-1") { }
        val b = EventHandler<Event>(key = "row-1") { error("never called") }

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `different keys and keyed versus unkeyed are not equal`() {
        val one = EventHandler<Event>(key = 1) { }
        val two = EventHandler<Event>(key = 2) { }
        val unkeyed = EventHandler<Event> { }

        assertNotEquals(one, two)
        assertNotEquals(one, unkeyed)
        assertNotEquals(unkeyed, one)
    }

    @Test
    fun `a hash set keeps one handler per key`() {
        val handlers =
            hashSetOf(
                EventHandler<Event> { },
                EventHandler<Event> { },
                EventHandler<Event>(key = 1) { },
                EventHandler<Event>(key = 1) { },
                EventHandler<Event>(key = 2) { },
            )

        assertEquals(3, handlers.size)
    }

    @Test
    fun `a handler is not equal to its own lambda nor to null`() {
        val lambda: (Event) -> Unit = { }
        val handler = EventHandler(handle = lambda)

        assertNotEquals(handler, lambda)
        assertNotEquals(handler, null)
    }

    @Test
    fun `invoke forwards events and toString shows the key`() {
        val received = mutableListOf<Event>()
        val handler = EventHandler<Event>(key = "row-1") { received += it }

        handler(Event.Ping)
        handler.invoke(Event.Pick(2))

        assertEquals(listOf(Event.Ping, Event.Pick(2)), received)
        assertEquals("EventHandler(key=row-1)", handler.toString())
    }
}
