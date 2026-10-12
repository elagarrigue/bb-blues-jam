package com.bbbjam.core.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One jam of `GET ?resource=jams`, exactly as the contract sends it (`docs/apps-script-api.md`,
 * Jam): every key present, each value a trimmed string or null. No defaults, so a missing key fails
 * the whole response: that is a script bug, not admin data. Interpreting the values is
 * [com.bbbjam.core.data.jams.JamsMapper]'s job.
 */
@Serializable
internal data class JamDto(
    val date: String?,
    val startTime: String?,
    val venue: String?,
    val status: String?,
    val setlist: List<SetlistRowDto>?,
    val setlistError: SetlistErrorDto?,
)

/** One row of a jam tab (`docs/apps-script-api.md`, Setlist row). */
@Serializable
internal data class SetlistRowDto(
    val position: String?,
    val songId: String?,
    val title: String?,
    val artist: String?,
    val key: String?,
    val slots: SlotsDto,
    val extraParticipants: String?,
)

/**
 * The eighteen slot cells, three per instrument, in the column order of `Lineup.MAX_INSTRUMENTS`.
 * Each is null (open), `"-"` (not part of this song's lineup) or a musician's name.
 *
 * Decoded by [SlotsDtoSerializer] through [SlotKeys], which accepts exactly the 18 keys, the 18 plus
 * the five legacy mirrors the current script sends, or the legacy seven of the script before the
 * three-slot layout (the other eleven then read `-`). Any other key set fails the whole response, so
 * a missing key is never read as an open slot.
 */
@Serializable(with = SlotsDtoSerializer::class)
internal data class SlotsDto(
    val guitar1: String?,
    val guitar2: String?,
    val guitar3: String? = null,
    val bass1: String?,
    val bass2: String? = null,
    val bass3: String? = null,
    val drums1: String?,
    val drums2: String? = null,
    val drums3: String? = null,
    val vocals1: String?,
    val vocals2: String? = null,
    val vocals3: String? = null,
    val harmonica1: String?,
    val harmonica2: String? = null,
    val harmonica3: String? = null,
    val keyboards1: String?,
    val keyboards2: String? = null,
    val keyboards3: String? = null,
) {
    /** The cells in column order, matching `Lineup.MAX_INSTRUMENTS` index by index. */
    fun cells(): List<String?> = listOf(
        guitar1, guitar2, guitar3,
        bass1, bass2, bass3,
        drums1, drums2, drums3,
        vocals1, vocals2, vocals3,
        harmonica1, harmonica2, harmonica3,
        keyboards1, keyboards2, keyboards3,
    )
}

internal object SlotsDtoSerializer : KSerializer<SlotsDto> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SlotsDto")

    override fun deserialize(decoder: Decoder): SlotsDto {
        val slots = (decoder as? JsonDecoder)?.decodeJsonElement() as? JsonObject
        val byKey = slots?.let(SlotKeys::cells)
            ?.let { cells -> SlotKeys.FIELDS.zip(cells) { key, cell -> key to cell.text() }.toMap() }
            ?: throw SerializationException("slots does not have the legacy seven or the eighteen keys")
        fun at(key: String) = byKey.getValue(key)
        return SlotsDto(
            guitar1 = at("guitar1"), guitar2 = at("guitar2"), guitar3 = at("guitar3"),
            bass1 = at("bass1"), bass2 = at("bass2"), bass3 = at("bass3"),
            drums1 = at("drums1"), drums2 = at("drums2"), drums3 = at("drums3"),
            vocals1 = at("vocals1"), vocals2 = at("vocals2"), vocals3 = at("vocals3"),
            harmonica1 = at("harmonica1"), harmonica2 = at("harmonica2"), harmonica3 = at("harmonica3"),
            keyboards1 = at("keyboards1"), keyboards2 = at("keyboards2"), keyboards3 = at("keyboards3"),
        )
    }

    private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

    override fun serialize(encoder: Encoder, value: SlotsDto) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("slots needs a JSON encoder")
        json.encodeJsonElement(
            JsonObject(
                SlotKeys.FIELDS.zip(value.cells()) { key, cell ->
                    key to (cell?.let(::JsonPrimitive) ?: JsonNull)
                }.toMap(),
            ),
        )
    }
}

/** A per-jam problem the script reported; [message] is English and for logs only. */
@Serializable
internal data class SetlistErrorDto(val code: String, val message: String)
