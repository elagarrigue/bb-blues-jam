package com.bbbjam.core.data.jams

import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.remote.SlotKeys
import com.bbbjam.core.data.remote.SlotsDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Which `slots` key sets are valid: the 18, the 18 plus the legacy mirrors, or the legacy seven. */
class SlotKeysTest {
    private val legacy = """{"guitar1":"Ana","guitar2":"-","bass":null,"drums":"Bo","vocals":null,
        "harmonica":"-","keyboards":null}"""
    private val mirrors = ""","bass":null,"drums":null,"vocals":null,"harmonica":null,"keyboards":null}"""

    private fun decode(text: String) = Json.decodeFromString(SlotsDto.serializer(), text)

    private fun obj(text: String) = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun `the legacy seven decode as the default columns and the other eleven read as not in the lineup`() {
        val dto = decode(legacy)

        assertEquals("Ana", dto.guitar1)
        assertEquals("-", dto.guitar2)
        assertNull(dto.bass1)
        assertEquals("Bo", dto.drums1)
        assertEquals("-", dto.harmonica1)
        listOf(dto.guitar3, dto.bass2, dto.bass3, dto.drums3, dto.vocals2, dto.keyboards3).forEach {
            assertEquals("-", it)
        }
    }

    @Test
    fun `the eighteen keys and the eighteen plus the legacy mirrors decode the same`() {
        val eighteen = Fixtures.slotsJson("bass1" to "Lu", "vocals2" to null)
        val mirrored = eighteen.removeSuffix("}") + mirrors.replace("\"bass\":null", "\"bass\":\"Lu\"")

        assertEquals(decode(eighteen), decode(mirrored))
        assertEquals("Lu", decode(mirrored).bass1)
        assertNull(decode(mirrored).vocals2)
    }

    @Test
    fun `a payload with neither the legacy nor the new first column key is rejected, never read as open`() {
        val noBass = legacy.replace("\"bass\":null,", "")
        val noBass1 = Fixtures.slotsJson().replace("\"bass1\":null,", "")
        val mismatch = Fixtures.slotsJson("bass1" to "Lu").removeSuffix("}") + mirrors
        val extra = legacy.removeSuffix("}") + ",\"guitar3\":null}"
        val notString = legacy.replace("\"bass\":null", "\"bass\":false")

        listOf(noBass, noBass1, mismatch, extra, notString, "[]", "\"x\"").forEach { text ->
            assertThrows(text, SerializationException::class.java) { decode(text) }
        }
    }

    @Test
    fun `cells place the legacy seven in the default columns and refuse any other key set`() {
        val cells = checkNotNull(SlotKeys.cells(obj(legacy)))

        assertEquals(18, cells.size)
        assertEquals(JsonPrimitive("Bo"), cells[6])
        assertEquals(JsonPrimitive("-"), cells[2])
        assertNull(SlotKeys.cells(buildJsonObject { put("guitar1", JsonPrimitive("Ana")) }))
        assertNull(SlotKeys.cells(obj(Fixtures.slotsJson().replace("\"keyboards3\":\"-\"", "\"other\":\"-\""))))
    }
}
