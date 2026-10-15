package com.anish1050.hires.data.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

// Keystore-backed storage for the app JWT, the last known entitlement and the
// key that encrypts downloads.
class AuthStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "auth", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var accessToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER, null)
        set(value) = prefs.edit().putString(KEY_USER, value).apply()

    /** JSON of the last entitlement the server returned, so offline playback can be checked without a network. */
    var entitlementJson: String?
        get() = prefs.getString(KEY_ENTITLEMENT, null)
        set(value) = prefs.edit().putString(KEY_ENTITLEMENT, value).apply()

    /** AES-128 key for downloaded audio, created once per install and never exported. */
    val downloadKey: ByteArray
        get() = prefs.getString(KEY_DOWNLOAD, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: ByteArray(16).also { key ->
                SecureRandom().nextBytes(key)
                prefs.edit().putString(KEY_DOWNLOAD, Base64.encodeToString(key, Base64.NO_WRAP)).commit()
            }

    val isSignedIn: Boolean get() = accessToken != null

    /** Signing out keeps the download key so a re-login can still play existing downloads. */
    fun clear() = prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).remove(KEY_ENTITLEMENT).apply()

    private companion object {
        const val KEY_TOKEN = "access_token"
        const val KEY_USER = "user_id"
        const val KEY_ENTITLEMENT = "entitlement"
        const val KEY_DOWNLOAD = "download_key"
    }
}
