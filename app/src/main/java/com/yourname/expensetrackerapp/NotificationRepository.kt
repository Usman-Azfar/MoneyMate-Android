package com.yourname.expensetrackerapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object NotificationRepository {
    private const val PREFS_NAME = "NotificationPrefs"
    private const val KEY_NOTIFICATIONS = "notifications"

    private val gson = Gson()

    /** Newest first. */
    fun getAll(context: Context): MutableList<NotificationEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_NOTIFICATIONS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<NotificationEntry>>() {}.type
        val all: MutableList<NotificationEntry> = gson.fromJson(json, type) ?: mutableListOf()
        return all.sortedByDescending { it.timestampMillis }.toMutableList()
    }

    fun add(context: Context, entry: NotificationEntry) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_NOTIFICATIONS, null)
        val type = object : TypeToken<MutableList<NotificationEntry>>() {}.type
        val all: MutableList<NotificationEntry> = if (json != null) gson.fromJson(json, type) else mutableListOf()
        all.add(entry)
        prefs.edit().putString(KEY_NOTIFICATIONS, gson.toJson(all)).apply()
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_NOTIFICATIONS, gson.toJson(emptyList<NotificationEntry>())).apply()
    }
}
