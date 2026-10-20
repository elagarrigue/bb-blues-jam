package com.bbbjam.core.data.remote

import kotlinx.serialization.Serializable

/**
 * The `song` object of a `createSong` or `updateSong` answer (`admin-catalog-tab`): what the server
 * wrote and read back. The repository validates it before it touches Room.
 */
@Serializable
internal data class CatalogSongEchoDto(val id: String, val title: String, val artist: String, val defaultKey: String)
