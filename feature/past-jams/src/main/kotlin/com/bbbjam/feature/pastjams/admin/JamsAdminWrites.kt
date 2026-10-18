package com.bbbjam.feature.pastjams.admin

import com.bbbjam.core.data.setlist.jamcreate.JamCreator
import com.bbbjam.core.data.setlist.jamedit.SetlistJamEditRepository
import com.bbbjam.core.data.setlist.jamstatus.SetlistJamStatusRepository
import com.bbbjam.core.data.setlist.publish.SetlistPublishRepository
import com.bbbjam.feature.pastjams.PastJamsPresenter

/**
 * The narrow write contracts of the Jams tab's admin section, grouped so [PastJamsPresenter] stays under
 * detekt's parameter limit (`admin-edit-delete-jam` C1): the status change, the publication, the creation
 * and the edit or deletion of a future jam (D-13, D-03). Constructor injection; never `SetlistRepository`.
 */
internal class JamsAdminWrites(
    val statuses: SetlistJamStatusRepository,
    val publishes: SetlistPublishRepository,
    val creator: JamCreator,
    val edits: SetlistJamEditRepository,
)
