package com.bbbjam.core.data.admin

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * The verified admin passphrase, in DataStore Preferences (file `admin_session`, excluded from
 * cloud backup and device transfer by `:app`'s backup rules). Admin mode is "a non-blank passphrase
 * is stored", so the flag and the credential can never disagree. Plaintext in app-private storage
 * (`docs/user-and-access-model.md`). Nothing here logs.
 */
internal class AdminCredentialStore(private val dataStore: DataStore<Preferences>) {

    fun observeIsAdmin(): Flow<Boolean> = preferences()
        .map { !it[PASSPHRASE].isNullOrBlank() }
        .distinctUntilChanged()

    /** The stored passphrase, read by [AdminWriter] for every write; null when logged out. */
    suspend fun passphrase(): String? = preferences().first()[PASSPHRASE]?.takeUnless { it.isBlank() }

    /** Throws [IOException] when the file cannot be written; nothing changes then. */
    suspend fun save(passphrase: String) {
        dataStore.edit { it[PASSPHRASE] = passphrase }
    }

    /** Throws [IOException] when the file cannot be written; nothing changes then. */
    suspend fun clear() {
        dataStore.edit { it.remove(PASSPHRASE) }
    }

    /** A file that cannot be read reads as logged out. */
    private fun preferences(): Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    companion object {
        const val FILE_NAME = "admin_session"
        private val PASSPHRASE = stringPreferencesKey("admin_passphrase")

        /** The one DataStore for [FILE_NAME]; DataStore allows a single active instance per file. */
        fun dataStore(context: Context): DataStore<Preferences> =
            dataStore { context.preferencesDataStoreFile(FILE_NAME) }

        /**
         * The DataStore over [produceFile], with a corrupt file read as empty (logged out). Tests
         * pass a temp file and their own [scope], which they cancel to simulate a restart.
         *
         * Okio storage with the Preferences serializer: the same file and format as the default
         * `File` storage, but its atomic replace goes through okio (`Files.move` where `java.nio`
         * exists). The `File` storage replaces with `File.renameTo` below API 26, which includes
         * the JVM unit tests, and on Windows that cannot replace an existing file: a save followed by
         * a clear failed there with "Unable to rename". One factory for the app and the tests.
         */
        fun dataStore(
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile: () -> File,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            storage = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = PreferencesSerializer,
                producePath = { produceFile().toOkioPath() },
            ),
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            migrations = emptyList(),
            scope = scope,
        )
    }
}
