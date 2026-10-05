package com.bbbjam.core.data.remote

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeTest {

    @Test
    fun `both catalog samples decode with every key`() {
        val seed = decode(Fixtures.sample("catalog-seed.json"))
        val edge = decode(Fixtures.sample("catalog-edge.json"))

        assertEquals(13, (seed as Decoded.Ok).value.size)
        assertEquals(
            SongDto("sin-tono", "Sin Tono", "Alguien", null, "Rápido", null, "difícil", "12.5"),
            (edge as Decoded.Ok).value.last(),
        )
    }

    @Test
    fun `an error envelope is a service failure with its code`() {
        val body = """{"schemaVersion":1,"error":{"code":"missing_header",""" +
            """"message":"Catalogo is missing required headers: tono_default"}}"""

        assertEquals(Decoded.Failed(DataFailure.Service("missing_header")), decode(body))
    }

    @Test
    fun `an HTML page is an invalid response that does not quote the body`() {
        val failure = invalid(decode("<!DOCTYPE html><html><body>https://example.invalid/secret</body></html>"))

        assertEquals("body is not a JSON object", failure.detail)
        assertFalse(failure.detail.contains("example.invalid"))
    }

    @Test
    fun `non-JSON and non-object bodies are invalid responses`() {
        listOf("", "not json", "[]", "1", "null").forEach { body ->
            assertEquals(body, "body is not a JSON object", invalid(decode(body)).detail)
        }
    }

    @Test
    fun `a schemaVersion other than 1 is an invalid response, even with an error`() {
        assertEquals("schemaVersion 2", invalid(decode("""{"schemaVersion":2,"songs":[]}""")).detail)
        assertEquals("schemaVersion missing", invalid(decode("""{"songs":[]}""")).detail)
        assertEquals("schemaVersion missing", invalid(decode("""{"schemaVersion":"1","songs":[]}""")).detail)
        assertEquals("schemaVersion 2", invalid(decode("""{"schemaVersion":2,"error":{"code":"x"}}""")).detail)
    }

    @Test
    fun `a missing songs key is an invalid response`() {
        assertEquals("missing songs", invalid(decode("""{"schemaVersion":1}""")).detail)
        assertEquals("missing songs", invalid(decode("""{"schemaVersion":1,"songs":null}""")).detail)
    }

    @Test
    fun `a malformed error is an invalid response`() {
        assertEquals("malformed error", invalid(decode("""{"schemaVersion":1,"error":{"message":"x"}}""")).detail)
        assertEquals("malformed error", invalid(decode("""{"schemaVersion":1,"error":"x"}""")).detail)
    }

    @Test
    fun `a song missing a key fails the whole response`() {
        val song = """{"id":"a","title":"A","artist":"B","defaultKey":"A","tempo":null,"tags":null,"difficulty":null}"""
        val failure = invalid(decode(Fixtures.catalogBody("[$song]")))

        assertTrue(failure.detail, failure.detail.startsWith("songs: "))
        assertTrue(failure.detail, failure.detail.contains("songsterrId"))
    }

    @Test
    fun `a numeric value fails the whole response`() {
        val song = """{"id":"a","title":"A","artist":"B","defaultKey":"A","tempo":null,"tags":null,""" +
            """"difficulty":null,"songsterrId":12345}"""
        val failure = invalid(decode(Fixtures.catalogBody("[$song]")))

        assertTrue(failure.detail, failure.detail.startsWith("songs: "))
    }

    @Test
    fun `songs that are not a list fail the whole response`() {
        assertTrue(invalid(decode("""{"schemaVersion":1,"songs":{}}""")).detail.startsWith("songs: "))
    }

    @Test
    fun `unknown keys are ignored so additive fields do not break the app`() {
        val song = """{"id":"a","title":"A","artist":"B","defaultKey":"A","tempo":null,"tags":null,""" +
            """"difficulty":null,"songsterrId":null,"mbid":"x"}"""
        val body = """{"schemaVersion":1,"generatedAt":"now","songs":[$song]}"""

        assertEquals(Decoded.Ok(listOf(SongDto("a", "A", "B", "A", null, null, null, null))), decode(body))
    }

    @Test
    fun `decodeOk accepts only schemaVersion 1 with ok true`() {
        assertEquals(Decoded.Ok(Unit), AppsScriptEnvelope.decodeOk("""{"schemaVersion":1,"ok":true}"""))
        assertEquals(Decoded.Ok(Unit), AppsScriptEnvelope.decodeOk("""{"schemaVersion":1,"ok":true,"extra":1}"""))
        val invalid = mapOf(
            """{"schemaVersion":1}""" to "ok is not true",
            """{"schemaVersion":1,"ok":false}""" to "ok is not true",
            """{"schemaVersion":1,"ok":"true"}""" to "ok is not true",
            """{"schemaVersion":1,"ok":null}""" to "ok is not true",
            """{"schemaVersion":2,"ok":true}""" to "schemaVersion 2",
            """{"ok":true}""" to "schemaVersion missing",
            "<!DOCTYPE html><html><body>Script function not found: doPost</body></html>" to "body is not a JSON object",
            "" to "body is not a JSON object",
        )
        invalid.forEach { (body, detail) ->
            assertEquals(body, Decoded.Failed(DataFailure.InvalidResponse(detail)), AppsScriptEnvelope.decodeOk(body))
        }
    }

    @Test
    fun `decodeOk reads an error envelope as a service failure, before ok`() {
        val body = """{"schemaVersion":1,"ok":true,"error":{"code":"invalid_passphrase","message":"m"}}"""

        assertEquals(Decoded.Failed(DataFailure.Service("invalid_passphrase")), AppsScriptEnvelope.decodeOk(body))
        assertEquals(
            Decoded.Failed(DataFailure.InvalidResponse("malformed error")),
            AppsScriptEnvelope.decodeOk("""{"schemaVersion":1,"error":{}}"""),
        )
    }

    private fun decode(body: String) = AppsScriptEnvelope.decode(body, "songs", SongDto.serializer())

    private fun invalid(decoded: Decoded<*>): DataFailure.InvalidResponse =
        (decoded as Decoded.Failed).failure as DataFailure.InvalidResponse
}
