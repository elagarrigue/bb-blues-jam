package com.bbbjam.core.data.assistant

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/** The incremental parser of the answer's `songs` array (`assistant-streaming`, Part B). Pure, no network. */
class ProposalStreamParserTest {

    private val songA = """{"songId":"a","title":"x","artist":"y","key":"C","reason":"Arranca fuerte"}"""
    private val songB = """{"songId":"b","title":"x","artist":"y","key":"C","reason":"Con \"comillas\" y \\ barra"}"""
    private val songC = """{"songId":"c","title":"x","artist":"y","key":"C","reason":"Llaves } { y ] [, ñandú á"}"""
    private val answer = """{"songs":[$songA,$songB,$songC],"message":"Listo","questions":["¿Algo más?"]}"""

    private fun feedAll(chunks: List<String>): List<String> {
        val parser = ProposalStreamParser()
        return chunks.flatMap { parser.feed(it) }
    }

    @Test
    fun `a whole answer in one chunk yields each song object`() {
        assertEquals(listOf(songA, songB, songC), feedAll(listOf(answer)))
    }

    @Test
    fun `a song is emitted only when its closing brace arrives`() {
        val parser = ProposalStreamParser()
        val cut = answer.indexOf(songB) + songB.length
        assertEquals(listOf(songA), parser.feed(answer.substring(0, cut - 1)))
        assertEquals(listOf(songB), parser.feed(answer.substring(cut - 1, cut)))
        assertEquals(listOf(songC), parser.feed(answer.substring(cut)))
    }

    @Test
    fun `every split of the answer into two chunks gives the same songs`() {
        for (i in 0..answer.length) {
            assertEquals("split at $i", listOf(songA, songB, songC), feedAll(listOf(answer.take(i), answer.drop(i))))
        }
    }

    @Test
    fun `fed char by char gives the same songs`() {
        assertEquals(listOf(songA, songB, songC), feedAll(answer.map { it.toString() }))
    }

    @Test
    fun `random chunk sizes give the same songs`() {
        val random = Random(42)
        repeat(200) {
            val chunks = ArrayList<String>()
            var at = 0
            while (at < answer.length) {
                val size = random.nextInt(1, 12)
                chunks += answer.substring(at, minOf(answer.length, at + size))
                at += size
            }
            assertEquals(listOf(songA, songB, songC), feedAll(chunks))
        }
    }

    @Test
    fun `an escaped quote does not close the string and an escaped backslash does not escape the next quote`() {
        val tricky = """{"songId":"t","reason":"termina en barra \\"}"""
        val text = """{"songs":[$tricky,$songA],"message":"m","questions":[]}"""
        assertEquals(listOf(tricky, songA), feedAll(listOf(text)))
        assertEquals(listOf(tricky, songA), feedAll(text.map { it.toString() }))
    }

    @Test
    fun `a unicode escape split across chunks is harmless`() {
        val song = """{"songId":"u","reason":"caf\u00e9 y \u007d llave"}"""
        val text = """{"songs":[$song],"message":"m","questions":[]}"""
        val cut = text.indexOf("\\u00e9") + 3
        assertEquals(listOf(song), feedAll(listOf(text.take(cut), text.drop(cut))))
    }

    @Test
    fun `braces and brackets inside strings do not change the depth`() {
        assertEquals(listOf(songC), feedAll(listOf("""{"songs":[$songC]}""")))
    }

    @Test
    fun `text and a json fence before the first brace are ignored`() {
        val text = "Claro, acá va:\n```json\n$answer\n```"
        assertEquals(listOf(songA, songB, songC), feedAll(listOf(text)))
        assertEquals(listOf(songA, songB, songC), feedAll(text.map { it.toString() }))
    }

    @Test
    fun `songs does not have to be the first key`() {
        val text = """{"message":"Hola, {no} soy un tema","questions":["¿a?"],"songs":[$songA,$songB]}"""
        assertEquals(listOf(songA, songB), feedAll(listOf(text)))
    }

    @Test
    fun `objects under other keys are never songs`() {
        val text = """{"jam":{"date":"2026-11-28","time":"21:00","venue":"x"},"songs":[$songA],""" +
            """"extra":[{"songId":"zzz"}],"message":"m","questions":[]}"""
        assertEquals(listOf(songA), feedAll(listOf(text)))
    }

    @Test
    fun `a key named songs inside a song or a string value is not the array`() {
        val text = """{"message":"\"songs\":[{\"songId\":\"no\"}]","jam":{"songs":[{"songId":"no"}]},""" +
            """"songs":[$songA]}"""
        assertEquals(listOf(songA), feedAll(listOf(text)))
    }

    @Test
    fun `nested arrays and objects inside a song stay in the song`() {
        val nested = """{"songId":"n","reason":"r","tags":[1,[2,{"x":"]"}]],"extra":{"deep":{"deeper":[]}}}"""
        assertEquals(listOf(nested, songA), feedAll(listOf("""{"songs":[$nested,$songA]}""")))
    }

    @Test
    fun `a malformed element is still returned as raw text for the validator to drop`() {
        val broken = """{"songId":,"reason" "x"}"""
        assertEquals(listOf(broken, songA), feedAll(listOf("""{"songs":[$broken,$songA]}""")))
    }

    @Test
    fun `non object elements of the array are skipped`() {
        assertEquals(listOf(songA), feedAll(listOf("""{"songs":["texto",42,null,$songA]}""")))
    }

    @Test
    fun `an unterminated tail emits nothing`() {
        val text = """{"songs":[$songA,{"songId":"b","reason":"se corta"""
        assertEquals(listOf(songA), feedAll(listOf(text)))
    }

    @Test
    fun `nothing is emitted after the songs array closes`() {
        val text = """{"songs":[$songA],"more":[{"songId":"late"}],"songs":[{"songId":"again"}]}"""
        assertEquals(listOf(songA), feedAll(listOf(text)))
    }

    @Test
    fun `an empty songs array and garbage never throw`() {
        assertEquals(emptyList<String>(), feedAll(listOf("""{"songs":[],"message":"m"}""")))
        assertEquals(emptyList<String>(), feedAll(listOf("]]]}}}{{{ \"\\", "\\\"", "[[[")))
        assertEquals(emptyList<String>(), feedAll(emptyList()))
    }

    @Test
    fun `reset starts over for the next round`() {
        val parser = ProposalStreamParser()
        assertEquals(emptyList<String>(), parser.feed("""{"songs":[{"songId":"half","reas"""))
        parser.reset()
        assertEquals(listOf(songA), parser.feed("""{"songs":[$songA]}"""))
        parser.reset()
        assertEquals(listOf(songA), parser.feed("""{"songs":[$songA]}"""))
    }
}
