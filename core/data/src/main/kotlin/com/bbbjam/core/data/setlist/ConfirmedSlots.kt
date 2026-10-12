package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.cache.JamSlotEntity
import com.bbbjam.core.model.Lineup
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** A confirmed cell is null (open) or a non-blank string; `-` is a string and means not in the lineup. */
internal fun validCell(cell: JsonElement): Boolean =
    cell == JsonNull || (cell is JsonPrimitive && cell.isString && cell.content.isNotBlank())

/**
 * The cached slot rows of a confirmed answer's 18 [cells] (from `SlotKeys.cells`), one per column not
 * holding `-`, keyed by the column's place among the 18. The position is filled in by the DAO.
 */
internal fun slotEntities(cells: List<JsonElement>, jamDate: String): List<JamSlotEntity> =
    cells.mapIndexedNotNull { column, cell ->
        val name = (cell as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
        if (name == "-") null else JamSlotEntity(jamDate, 0, column, Lineup.MAX_INSTRUMENTS[column].name, name)
    }
