package com.bbbjam.core.data.presence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.bbbjam.core.data.admin.AdminCredentialStore
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * The random id this install announces under (DataStore file `presence_holder`, excluded from
 * backup and transfer so a restored copy never shares an id with another phone). Not a user
 * identity: it only lets the server skip an install's own entry.
 */
internal class PresenceHolderStore(private val dataStore: DataStore<Preferences>) {

    /** The stored id, created in one atomic edit on first use; null when the file cannot be used. */
    suspend fun holderId(): String? = try {
        dataStore.data.first()[HOLDER_ID]?.takeUnless(String::isBlank) ?: createId()
    } catch (_: IOException) {
        null
    }

    private suspend fun createId(): String {
        var id = ""
        dataStore.edit { preferences ->
            id = preferences[HOLDER_ID]?.takeUnless(String::isBlank) ?: UUID.randomUUID().toString().also {
                preferences[HOLDER_ID] = it
            }
        }
        return id
    }

    companion object {
        const val FILE_NAME = "presence_holder"
        private val HOLDER_ID = stringPreferencesKey("holder_id")

        fun dataStore(context: Context): DataStore<Preferences> =
            AdminCredentialStore.dataStore { context.preferencesDataStoreFile(FILE_NAME) }
    }
}
