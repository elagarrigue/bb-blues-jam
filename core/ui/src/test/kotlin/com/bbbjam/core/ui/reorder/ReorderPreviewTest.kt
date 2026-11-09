package com.bbbjam.core.ui.reorder

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderPreviewTest {
    private val list = listOf("a", "b", "c", "d")

    @Test
    fun `moving down inserts after the slot row`() {
        assertEquals(listOf("a", "c", "b", "d"), list.withDragged("b", DropSlot.After("c")))
        assertEquals(listOf("a", "c", "d", "b"), list.withDragged("b", DropSlot.After("d")))
    }

    @Test
    fun `moving up inserts before the slot row`() {
        assertEquals(listOf("c", "a", "b", "d"), list.withDragged("c", DropSlot.Before("a")))
    }

    @Test
    fun `the same place keeps the order`() {
        assertEquals(list, list.withDragged("b", DropSlot.Before("c")))
        assertEquals(list, list.withDragged("b", DropSlot.After("a")))
    }

    @Test
    fun `a null slot, an unknown key or the dragged key itself change nothing`() {
        assertEquals(list, list.withDragged("b", null))
        assertEquals(list, list.withDragged("b", DropSlot.Before("zzz")))
        assertEquals(list, list.withDragged("zzz", DropSlot.Before("a")))
        assertEquals(list, list.withDragged("b", DropSlot.Before("b")))
    }
}
