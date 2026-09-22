package com.meetdheeran.ledger.core

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.prefsStore by preferencesDataStore(name = "ledger")

/**
 * The app password, and the handful of flags the app needs to remember.
 *
 * The password is never stored, only a PBKDF2 hash of it with a random salt, so
 * the stored value cannot be read back into the password that produced it.
 *
 * Scope note, so this is not mistaken for more than it is: this gates the
 * screen. The database itself is not encrypted - that was option B, and Dheer
 * chose A. Anyone with the unlocked phone and developer access can read the
 * database file directly.
 */
object Lock {

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    private val KEY_PASSWORD = stringPreferencesKey("password_hash")

    suspend fun isSet(context: Context): Boolean =
        context.prefsStore.data.first()[KEY_PASSWORD]?.isNotBlank() == true

    suspend fun setPassword(context: Context, password: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt)
        val stored = "${salt.b64()}:${hash.b64()}"
        context.prefsStore.edit { it[KEY_PASSWORD] = stored }
    }

    suspend fun verify(context: Context, password: String): Boolean {
        val stored = context.prefsStore.data.first()[KEY_PASSWORD] ?: return false
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = runCatching { parts[0].unB64() }.getOrNull() ?: return false
        val expected = runCatching { parts[1].unB64() }.getOrNull() ?: return false
        // Constant-time comparison: a length- or content-dependent early exit
        // leaks information about the stored hash.
        return MessageDigest.isEqual(derive(password, salt), expected)
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.unB64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
}

object Prefs {

    private val KEY_BACKFILLED = booleanPreferencesKey("backfill_done")

    suspend fun backfillDone(context: Context): Boolean =
        context.prefsStore.data.first()[KEY_BACKFILLED] ?: false

    suspend fun setBackfillDone(context: Context, done: Boolean) {
        context.prefsStore.edit { it[KEY_BACKFILLED] = done }
    }
}
