// Add this new class: FirstRunManager.kt
package com.example.billingapp.utils

import android.content.Context
import android.content.SharedPreferences

class FirstRunManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "first_run_prefs"
        private const val KEY_FIRST_RUN = "is_first_run"
        private const val KEY_FIRST_RUN_AFTER_INSTALL = "first_run_after_install"
    }

    fun isFirstRun(): Boolean {
        return sharedPreferences.getBoolean(KEY_FIRST_RUN, true)
    }

    fun setFirstRunCompleted() {
        sharedPreferences.edit()
            .putBoolean(KEY_FIRST_RUN, false)
            .apply()
    }

    fun isFirstRunAfterInstall(): Boolean {
        return sharedPreferences.getBoolean(KEY_FIRST_RUN_AFTER_INSTALL, true)
    }

    fun setFirstRunAfterInstallCompleted() {
        sharedPreferences.edit()
            .putBoolean(KEY_FIRST_RUN_AFTER_INSTALL, false)
            .apply()
    }

    fun clearAllFlags() {
        sharedPreferences.edit()
            .clear()
            .apply()
    }
}
