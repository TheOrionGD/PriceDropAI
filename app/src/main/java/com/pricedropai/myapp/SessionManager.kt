package com.pricedropai.myapp

import android.content.Context
import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * SessionManager handles secure local authentication and active session state.
 * Passwords are never stored in plain text; they are hashed with SHA-256 and user salt.
 */
class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_session_secure", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_PASSWORD_HASH = "pwd_hash_"
        private const val SALT_PREFIX = "pricedrop_salt_v1_"
        private const val KEY_THEME_MODE = "theme_mode" // "SYSTEM", "LIGHT", "DARK"
        private const val KEY_ACCENT_COLOR = "accent_color" // hex e.g. "#6366F1"
        private const val KEY_PHONE_NOTIFICATIONS_ENABLED = "phone_notifications_enabled"
        private const val KEY_ALERT_SOUND_ENABLED = "alert_sound_enabled"
        private const val KEY_BACKGROUND_CHECK_INTERVAL = "background_check_interval"
        private const val KEY_LAST_CHECK_TIMESTAMP = "last_check_timestamp"
        private const val KEY_LAST_CHECK_DIAGNOSTICS = "last_check_diagnostics"
    }

    /**
     * Persisted background price check frequency
     */
    fun getPriceCheckInterval(): com.pricedropai.myapp.core.model.PriceCheckInterval {
        val name = prefs.getString(KEY_BACKGROUND_CHECK_INTERVAL, com.pricedropai.myapp.core.model.PriceCheckInterval.EVERY_6_HOURS.name)
        return try {
            com.pricedropai.myapp.core.model.PriceCheckInterval.valueOf(name ?: com.pricedropai.myapp.core.model.PriceCheckInterval.EVERY_6_HOURS.name)
        } catch (_: Exception) {
            com.pricedropai.myapp.core.model.PriceCheckInterval.fromDisplayName(name)
        }
    }

    fun setPriceCheckInterval(interval: com.pricedropai.myapp.core.model.PriceCheckInterval) {
        prefs.edit().putString(KEY_BACKGROUND_CHECK_INTERVAL, interval.name).apply()
    }

    fun getLastCheckTimestamp(): Long = prefs.getLong(KEY_LAST_CHECK_TIMESTAMP, 0L)

    fun setLastCheckTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK_TIMESTAMP, timestamp).apply()
    }

    fun getLastCheckDiagnostics(): String? = prefs.getString(KEY_LAST_CHECK_DIAGNOSTICS, null)

    fun setLastCheckDiagnostics(diagnostics: String) {
        prefs.edit().putString(KEY_LAST_CHECK_DIAGNOSTICS, diagnostics).apply()
    }

    /**
     * Whether app activity and price drop messages should be delivered to phone status bar / lock screen
     */
    fun isPhoneNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_PHONE_NOTIFICATIONS_ENABLED, true)

    fun setPhoneNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PHONE_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    /**
     * Whether phone notifications produce an audible chime and haptic vibration
     */
    fun isSoundEnabled(): Boolean = prefs.getBoolean(KEY_ALERT_SOUND_ENABLED, true)

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALERT_SOUND_ENABLED, enabled).apply()
    }

    private fun hashPassword(username: String, rawPassword: String): String {
        val input = "$SALT_PREFIX${username.lowercase().trim()}_$rawPassword"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Theme mode: "SYSTEM", "LIGHT", "DARK"
     */
    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    /**
     * Accent color hex: e.g. "#6366F1" (Indigo), "#10B981" (Emerald), "#EC4899" (Pink), "#F59E0B" (Amber), "#3B82F6" (Ocean Blue), "#8B5CF6" (Violet), "#06B6D4" (Cyan)
     */
    fun getAccentColorHex(): String = prefs.getString(KEY_ACCENT_COLOR, "#6366F1") ?: "#6366F1"

    fun setAccentColorHex(hex: String) {
        prefs.edit().putString(KEY_ACCENT_COLOR, hex).apply()
    }

    /**
     * Registers a new user or validates credentials for an existing local account.
     */
    fun loginOrRegister(name: String, rawPassword: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank() || rawPassword.length < 4) return false

        val userKey = "$KEY_PASSWORD_HASH${cleanName.lowercase()}"
        val existingHash = prefs.getString(userKey, null)
        val computedHash = hashPassword(cleanName, rawPassword)

        if (existingHash == null) {
            // New user registration
            prefs.edit().apply {
                putString(userKey, computedHash)
                putBoolean(KEY_IS_LOGGED_IN, true)
                putString(KEY_USER_NAME, cleanName)
                apply()
            }
            return true
        } else if (existingHash == computedHash) {
            // Existing user password verified
            prefs.edit().apply {
                putBoolean(KEY_IS_LOGGED_IN, true)
                putString(KEY_USER_NAME, cleanName)
                apply()
            }
            return true
        } else {
            // Incorrect password
            return false
        }
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""

    fun logout() {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_USER_NAME)
            apply()
        }
    }
}