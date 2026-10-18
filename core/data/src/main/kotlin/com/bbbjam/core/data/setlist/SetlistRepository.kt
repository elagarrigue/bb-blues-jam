package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.setlist.add.SetlistSongAddRepository
import com.bbbjam.core.data.setlist.assign.SetlistAssignmentRepository
import com.bbbjam.core.data.setlist.clear.SetlistSlotClearRepository
import com.bbbjam.core.data.setlist.extras.SetlistExtraParticipantRepository
import com.bbbjam.core.data.setlist.jamcreate.SetlistJamCreationRepository
import com.bbbjam.core.data.setlist.jamedit.SetlistJamEditRepository
import com.bbbjam.core.data.setlist.jamstatus.SetlistJamStatusRepository
import com.bbbjam.core.data.setlist.key.SetlistKeyChangeRepository
import com.bbbjam.core.data.setlist.lineup.SetlistLineupRepository
import com.bbbjam.core.data.setlist.move.SetlistMoveRepository
import com.bbbjam.core.data.setlist.publish.SetlistPublishRepository
import com.bbbjam.core.data.setlist.remove.SetlistSongRemoveRepository

/**
 * The setlist mutations of the upcoming jam (`admin-add-song-to-setlist`,
 * `admin-remove-song-from-setlist`, `admin-set-key`, `admin-adjust-lineup`,
 * `admin-publish-setlist`, D-13): plain repository functions, usable with no UI, so the action
 * registry and the phase 2 assistant can call them.
 * Every write goes through the admin POST path; Apps Script authorizes it, never the local flag.
 *
 * This is only the union of one narrow contract per mutation, each with its write, observe and
 * dismiss. `:app` binds every contract to the one instance; a consumer asks for the contracts it
 * calls, never for this union. Ids are global across the contracts (one counter), and a dismiss
 * removes a failed entry of its own kind only.
 */
interface SetlistRepository :
    SetlistSongAddRepository,
    SetlistSongRemoveRepository,
    SetlistKeyChangeRepository,
    SetlistLineupRepository,
    SetlistAssignmentRepository,
    SetlistSlotClearRepository,
    SetlistMoveRepository,
    SetlistExtraParticipantRepository,
    SetlistPublishRepository,
    SetlistJamStatusRepository,
    SetlistJamEditRepository,
    SetlistJamCreationRepository
