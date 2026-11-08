package com.rafat.canvos.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Session(val domain: String, val token: String)

/**
 * Stores the Canvas domain and access token. The token is encrypted with an
 * AES-GCM key that lives in the Android Keystore and never leaves the device.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun load(): Session? {
        val domain = prefs.getString(KEY_DOMAIN, null) ?: return null
        val blob = prefs.getString(KEY_TOKEN, null) ?: return null
        val token = runCatching { decrypt(blob) }.getOrNull() ?: return null
        return Session(domain, token)
    }

    fun save(session: Session) {
        prefs.edit()
            .putString(KEY_DOMAIN, session.domain)
            .putString(KEY_TOKEN, encrypt(session.token))
            .apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_DOMAIN).apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        return String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    }

    private companion object {
        const val ALIAS = "canvos_token_key"
        const val KEY_DOMAIN = "domain"
        const val KEY_TOKEN = "token"
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val showGrades: Boolean = true,
    val colorOverlay: Boolean = true,
    /** New announcements, messages and grades (checked every 15 minutes). */
    val notifications: Boolean = true,
    val dueReminders: DueReminder = DueReminder.D1H1,
    /** Debug-only: refuse every request that would change data on Canvas. */
    val readOnly: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun read() = AppSettings(
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean("dynamic_color", false),
        showGrades = prefs.getBoolean("show_grades", true),
        colorOverlay = prefs.getBoolean("color_overlay", true),
        // New key: the old switch was off by default, so most installs never got notifications.
        notifications = prefs.getBoolean("notifications_v2", true),
        dueReminders = runCatching { DueReminder.valueOf(prefs.getString("due_reminders", DueReminder.D1H1.name)!!) }.getOrDefault(DueReminder.D1H1),
        readOnly = prefs.getBoolean("read_only", false),
    )

    fun update(f: (AppSettings) -> AppSettings) {
        val n = f(_state.value)
        prefs.edit()
            .putString("theme", n.theme.name)
            .putBoolean("dynamic_color", n.dynamicColor)
            .putBoolean("show_grades", n.showGrades)
            .putBoolean("color_overlay", n.colorOverlay)
            .putBoolean("notifications_v2", n.notifications)
            .putString("due_reminders", n.dueReminders.name)
            .putBoolean("read_only", n.readOnly)
            .apply()
        _state.value = n
    }
}
