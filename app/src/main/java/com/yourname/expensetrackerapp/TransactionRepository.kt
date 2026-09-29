package com.yourname.expensetrackerapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Single source of truth for transactions, shared by Home, History and Statistics — every screen
 * reads from this local SharedPreferences cache only, never straight from Firestore, to keep the
 * app fast and mostly offline-capable. Mutations also notify SyncManager, which mirrors them to
 * Firestore (queueing them if offline) and keeps this cache trimmed to the most recent
 * [LOCAL_CACHE_LIMIT] records once they're safely synced — see SyncManager for that half.
 */
object TransactionRepository {
    private const val PREFS_NAME = "ExpenseTrackerPrefs"
    private const val KEY_TRANSACTIONS = "transactions"

    /** Older, already-synced transactions beyond this count are trimmed from local storage —
     *  they still live in Firestore, just no longer duplicated on-device. */
    const val LOCAL_CACHE_LIMIT = 200

    private val gson = Gson()

    fun getAll(context: Context): MutableList<Transaction> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TRANSACTIONS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<Transaction>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun saveAll(context: Context, transactions: List<Transaction>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TRANSACTIONS, gson.toJson(transactions)).apply()
    }

    fun add(context: Context, transaction: Transaction) {
        val all = getAll(context)
        all.add(0, transaction)
        saveAll(context, all)
        SyncManager.queueAdd(context, transaction)
    }

    fun delete(context: Context, transaction: Transaction) {
        val all = getAll(context)
        all.removeAll { it.id == transaction.id }
        saveAll(context, all)
        SyncManager.queueDelete(context, transaction.id)
    }

    /** Replaces the transaction sharing [transaction]'s id with the edited version, in place
     *  (keeping its position in the list). Syncs the same way as [add] — a Firestore set() on an
     *  existing document id overwrites it, so an edit and a fresh add use the same sync path. */
    fun update(context: Context, transaction: Transaction) {
        val all = getAll(context)
        val index = all.indexOfFirst { it.id == transaction.id }
        if (index == -1) return
        all[index] = transaction
        saveAll(context, all)
        SyncManager.queueAdd(context, transaction)
    }

    /** Wholesale replace of the local cache — used when pulling a fresh snapshot down from
     *  Firestore right after login, and when trimming down to [LOCAL_CACHE_LIMIT]. */
    fun replaceAll(context: Context, transactions: List<Transaction>) {
        saveAll(context, transactions)
    }

    /** Drops the oldest transactions beyond [LOCAL_CACHE_LIMIT], but only ones SyncManager
     *  doesn't still have queued for upload — trimming an unsynced record would lose it for
     *  good, since local storage would then be its only copy. */
    fun trimToLocalCacheLimit(context: Context) {
        val all = getAll(context)
        if (all.size <= LOCAL_CACHE_LIMIT) return

        val pendingIds = SyncManager.pendingIds(context)
        val kept = all.filterIndexed { index, transaction -> index < LOCAL_CACHE_LIMIT || transaction.id in pendingIds }
        if (kept.size != all.size) saveAll(context, kept)
    }
}
