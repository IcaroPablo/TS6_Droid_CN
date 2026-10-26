package dev.tsdroid.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.Base64
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.userAudioDataStore by preferencesDataStore(
    name = "user_audio",
    produceMigrations = { listOf(EncryptPlaintextUserAudio) },
)

// All entries in one encrypted JSON object (see [DeviceCipher]): the keys name the
// server and the friends' uids, and the values say whom the user muted.
private val KEY_ENTRIES_ENC = stringPreferencesKey("entries_enc")

private const val TAG = "UserAudioStore"

private val userAudioCipher = DeviceCipher()

private fun readEntries(prefs: Preferences): JSONObject {
    val stored = prefs[KEY_ENTRIES_ENC] ?: return JSONObject()
    return try {
        JSONObject(userAudioCipher.decrypt(Base64.getDecoder().decode(stored)).decodeToString())
    } catch (e: Exception) {
        // The key is gone (app data cleared, Keystore reset) or the value is damaged.
        Log.e(TAG, "Failed to decrypt per-user audio settings", e)
        JSONObject()
    }
}

private fun MutablePreferences.writeEntries(entries: JSONObject) {
    this[KEY_ENTRIES_ENC] =
        Base64.getEncoder().encodeToString(userAudioCipher.encrypt(entries.toString().encodeToByteArray()))
}

/**
 * Encrypts what builds before encryption stored as one plaintext key per entry
 * ("volume|<address>|<uid>" floats, "muted|<address>|<uid>" booleans) before the
 * first read.
 */
private object EncryptPlaintextUserAudio : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap().keys.any { it != KEY_ENTRIES_ENC }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            val entries = readEntries(this)
            for ((key, value) in currentData.asMap()) {
                if (key == KEY_ENTRIES_ENC) continue
                if (!entries.has(key.name)) entries.put(key.name, value)
                remove(key)
            }
            writeEntries(entries)
        }

    override suspend fun cleanUp() {}
}

/**
 * Persisted per-user audio settings, keyed by server address + permanent
 * client uid (client ids are reassigned every connection, so they must never
 * be persisted). A null setter removes the entry, meaning "default".
 */
class UserAudioStore(private val context: Context) {

    suspend fun volumeDb(address: String, uid: String): Float? {
        val entries = readEntries(context.userAudioDataStore.data.first())
        val key = volumeKey(address, uid)
        return if (entries.has(key)) entries.getDouble(key).toFloat() else null
    }

    suspend fun setVolumeDb(address: String, uid: String, db: Float?) {
        update(volumeKey(address, uid), db?.toDouble())
    }

    suspend fun muted(address: String, uid: String): Boolean? {
        val entries = readEntries(context.userAudioDataStore.data.first())
        val key = mutedKey(address, uid)
        return if (entries.has(key)) entries.getBoolean(key) else null
    }

    suspend fun setMuted(address: String, uid: String, muted: Boolean?) {
        update(mutedKey(address, uid), muted)
    }

    private suspend fun update(key: String, value: Any?) {
        context.userAudioDataStore.edit { prefs ->
            val entries = readEntries(prefs)
            if (value == null) entries.remove(key) else entries.put(key, value)
            prefs.writeEntries(entries)
        }
    }

    private fun volumeKey(address: String, uid: String) = "volume|$address|$uid"

    private fun mutedKey(address: String, uid: String) = "muted|$address|$uid"
}
