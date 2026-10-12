package com.bbbjam.core.data.remote

import com.bbbjam.core.model.Lineup
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The keys of a `slots` object (`docs/apps-script-api.md`) and the one place that decides which key
 * sets are valid, for the jams read and for the confirmed answers of `setSlotCount` and `clearSlot`.
 *
 * Exactly one of three sets is accepted, never a subset, so a missing key can never be read as an
 * open slot:
 * - the 18 keys, three per instrument (`guitar1` ... `keyboards3`), in [Lineup.MAX_INSTRUMENTS] order;
 * - those 18 plus the five legacy keys (`bass`, `drums`, `vocals`, `harmonica`, `keyboards`), each equal
 *   to its `...1` key: what the current script sends, so an older app keeps reading;
 * - the legacy seven (`guitar1`, `guitar2`, `bass`, `drums`, `vocals`, `harmonica`, `keyboards`): the
 *   script before the three-slot layout. Its slots are the default seven; the other eleven columns
 *   read `-`.
 */
internal object SlotKeys {
    private const val NOT_IN_LINEUP = "-"
    private const val SLOTS_PER_INSTRUMENT = 3

    /** The 18 keys in column order. */
    val FIELDS: List<String> = listOf("guitar", "bass", "drums", "vocals", "harmonica", "keyboards")
        .flatMap { name -> (1..SLOTS_PER_INSTRUMENT).map { "$name$it" } }
        .also { check(it.size == Lineup.MAX_INSTRUMENTS.size) { "One key per slot column" } }

    /** Legacy key to the `...1` key it mirrors. */
    private val LEGACY_FIRST: Map<String, String> = mapOf(
        "bass" to "bass1",
        "drums" to "drums1",
        "vocals" to "vocals1",
        "harmonica" to "harmonica1",
        "keyboards" to "keyboards1",
    )

    private val LEGACY_KEYS: Set<String> = setOf("guitar1", "guitar2") + LEGACY_FIRST.keys

    /**
     * The 18 cells of [slots] in column order (null is open, `"-"` is not in the lineup), or null when
     * the keys are not one of the valid sets, a legacy key disagrees with its `...1` key, or a value
     * is neither null nor a string.
     */
    fun cells(slots: JsonObject): List<JsonElement>? {
        val keys = slots.keys
        val cells = when (keys) {
            FIELDS.toSet() -> FIELDS.map { slots.getValue(it) }

            FIELDS.toSet() + LEGACY_FIRST.keys ->
                FIELDS.map { slots.getValue(it) }.takeIf { LEGACY_FIRST.all { (old, now) -> slots[old] == slots[now] } }

            LEGACY_KEYS -> legacyCells(slots)

            else -> null
        }
        return cells?.takeIf { all -> all.all { it == JsonNull || (it is JsonPrimitive && it.isString) } }
    }

    private fun legacyCells(slots: JsonObject): List<JsonElement> {
        val cells = MutableList<JsonElement>(FIELDS.size) { JsonPrimitive(NOT_IN_LINEUP) }
        LEGACY_KEYS.forEachIndexed { index, key -> cells[Lineup.DEFAULT_COLUMNS[index]] = slots.getValue(key) }
        return cells
    }
}
