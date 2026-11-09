package com.bbbjam.core.data.musician

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.bbbjam.core.data.admin.AdminCredentialStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [MusicianFilterPreference] in its own DataStore file (`musician_filter`), built by the same okio
 * factory as the admin store. A file that cannot be read reads as no selection. Nothing here logs
 * the name.
 */
internal class DefaultMusicianFilterPreference(private val dataStore: DataStore<Preferences>) :
    MusicianFilterPreference {

    override fun observeSelected(): Flow<String?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[NAME]?.takeUnless(String::isBlank) }
        .distinctUntilChanged()

    /** Throws [IOException] when the file cannot be written; nothing changes then. */
    override suspend fun select(name: String?) {
        dataStore.edit { preferences ->
            if (name.isNullOrBlank()) preferences.remove(NAME) else preferences[NAME] = name
        }
    }

    companion object {
        const val FILE_NAME = "musician_filter"
        private val NAME = stringPreferencesKey("musician_name")

        fun dataStore(context: Context): DataStore<Preferences> =
            AdminCredentialStore.dataStore { context.preferencesDataStoreFile(FILE_NAME) }
    }
}
