package com.example.pricedropai

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    fun saveLogin(name: String) {
        prefs.edit().apply {
            putBoolean("is_logged_in", true)
            putString("user_name", name)
            apply()
        }
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean("is_logged_in", false)

    fun getUserName(): String = prefs.getString("user_name", "") ?: ""

    fun logout() {
        prefs.edit().clear().apply()
    }
}