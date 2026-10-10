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
    }

    private fun hashPassword(username: String, rawPassword: String): String {
        val input = "$SALT_PREFIX${username.lowercase().trim()}_$rawPassword"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
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