package com.example.billingapp_2.ui.auth

import android.content.Context
import androidx.core.content.edit

object SessionHolder {
    private const val PREF_NAME = "BillingAppPrefs"
    private const val KEY_ADMIN_PASSWORD = "admin_password"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    var adminPassword: String? = null
        private set

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        adminPassword = prefs.getString(KEY_ADMIN_PASSWORD, null)
    }

    fun saveAdminPassword(context: Context, password: String) {
        adminPassword = password
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_ADMIN_PASSWORD, password)
        }
    }

    fun clearSession(context: Context) {
        adminPassword = null
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_ADMIN_PASSWORD)
            putBoolean(KEY_IS_LOGGED_IN, false)
        }
    }

    fun setLoggedIn(context: Context, loggedIn: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_IS_LOGGED_IN, loggedIn)
        }
    }

    fun isLoggedIn(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_LOGGED_IN, false)
    }
}