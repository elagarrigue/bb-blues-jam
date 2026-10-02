package com.bbbjam.core.data.cache

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * One cached jam. [date] is ISO `YYYY-MM-DD`, [startTime] `HH:MM`, [status] the `JamStatus` name.
 * [setlistState] is `AVAILABLE`, `WITHHELD` or `UNAVAILABLE`; [setlistProblem] holds the
 * `SetlistProblem` name of an unavailable setlist and [droppedRows] the rows left out of an
 * available one. [sheetOrder] is the 1-based order of the kept jams in the response.
 */
@Entity(tableName = "jam")
internal data class JamEntity(
    @PrimaryKey val date: String,
    @ColumnInfo(name = "sheet_order") val sheetOrder: Int,
    @ColumnInfo(name = "start_time") val startTime: String,
    val venue: String,
    val status: String,
    @ColumnInfo(name = "setlist_state") val setlistState: String,
    @ColumnInfo(name = "setlist_problem") val setlistProblem: String?,
    @ColumnInfo(name = "dropped_rows") val droppedRows: Int,
)

/**
 * One song of an available setlist, identified by (`jam_date`, `position`), the Sheet's
 * `posicion`. [titleCopy] and [artistCopy] are the tab's copies, used only when [songId] is not in
 * the catalog ([JamSongResolved]). [key] is the tab's, never the catalog default (D-08).
 */
@Entity(
    tableName = "jam_song",
    primaryKeys = ["jam_date", "position"],
    foreignKeys = [
        ForeignKey(
            entity = JamEntity::class,
            parentColumns = ["date"],
            childColumns = ["jam_date"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class JamSongEntity(
    @ColumnInfo(name = "jam_date") val jamDate: String,
    val position: Int,
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "title_copy") val titleCopy: String,
    @ColumnInfo(name = "artist_copy") val artistCopy: String,
    val key: String,
)

/**
 * One lineup slot. [columnIndex] is the 0-based slot column (`Guitarra 1` = 0 … `Teclados` = 6):
 * the slot's identity in the Sheet (`docs/sheet-schema.md`, Identifiers). A `-` column has no row.
 * [instrument] is the `Instrument` name; [musicianName] is null for an open slot.
 */
@Entity(
    tableName = "jam_slot",
    primaryKeys = ["jam_date", "position", "column_index"],
    foreignKeys = [
        ForeignKey(
            entity = JamSongEntity::class,
            parentColumns = ["jam_date", "position"],
            childColumns = ["jam_date", "position"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class JamSlotEntity(
    @ColumnInfo(name = "jam_date") val jamDate: String,
    val position: Int,
    @ColumnInfo(name = "column_index") val columnIndex: Int,
    val instrument: String,
    @ColumnInfo(name = "musician_name") val musicianName: String?,
)

/** One `Otros` entry; [entryOrder] is its 1-based order among the song's valid entries. */
@Entity(
    tableName = "jam_extra",
    primaryKeys = ["jam_date", "position", "entry_order"],
    foreignKeys = [
        ForeignKey(
            entity = JamSongEntity::class,
            parentColumns = ["jam_date", "position"],
            childColumns = ["jam_date", "position"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class JamExtraEntity(
    @ColumnInfo(name = "jam_date") val jamDate: String,
    val position: Int,
    @ColumnInfo(name = "entry_order") val entryOrder: Int,
    val name: String,
    val instrument: String,
)

/**
 * A setlist song with its title and artist resolved: the catalog's when [JamSongEntity.songId] is
 * cached there, the tab's copy otherwise (`docs/sheet-schema.md`). A view, so replacing the catalog
 * re-emits the jams with the new titles without refetching them.
 */
@DatabaseView(
    viewName = "jam_song_resolved",
    value = """
        SELECT s.jam_date, s.position, s.song_id, s.key,
            COALESCE(c.title, s.title_copy) AS title,
            COALESCE(c.artist, s.artist_copy) AS artist
        FROM jam_song AS s LEFT JOIN catalog_song AS c ON c.id = s.song_id
    """,
)
internal data class JamSongResolved(
    @ColumnInfo(name = "jam_date") val jamDate: String,
    val position: Int,
    @ColumnInfo(name = "song_id") val songId: String,
    val key: String,
    val title: String,
    val artist: String,
)

/** A jam with every row that hangs off it, as one `@Transaction` read returns it. */
internal data class JamWithChildren(
    @Embedded val jam: JamEntity,
    @Relation(parentColumn = "date", entityColumn = "jam_date")
    val songs: List<JamSongResolved>,
    @Relation(parentColumn = "date", entityColumn = "jam_date")
    val slots: List<JamSlotEntity>,
    @Relation(parentColumn = "date", entityColumn = "jam_date")
    val extras: List<JamExtraEntity>,
)
