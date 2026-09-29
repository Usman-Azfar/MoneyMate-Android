package com.yourname.expensetrackerapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Bridges the local SharedPreferences transaction cache (TransactionRepository) with Firestore.
 * Every screen keeps reading transactions from that local cache only — this object handles the
 * two moments that need the network: pushing a local change up, and pulling the initial snapshot
 * down right after login.
 *
 * Offline writes are recorded in a small pending-operations queue (its own SharedPreferences
 * entry) and replayed once connectivity returns. Firestore documents are read/written as plain
 * maps rather than via Firestore's automatic POJO mapping, to sidestep any ambiguity around
 * Kotlin data classes and no-argument constructors.
 */
object SyncManager {
    private const val PREFS_NAME = "SyncPrefs"
    private const val KEY_PENDING_OPS = "pendingOps"
    private const val OP_ADD = "ADD"
    private const val OP_DELETE = "DELETE"

    private val gson = Gson()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private data class PendingOp(val id: String, val operation: String, val transaction: Transaction?)

    private fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    private fun transactionsCollection(uid: String) =
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("transactions")

    // ---- Firestore <-> Transaction mapping (manual, to avoid POJO-mapping pitfalls) -----------

    private fun toMap(transaction: Transaction): Map<String, Any?> = mapOf(
        "id" to transaction.id,
        "description" to transaction.description,
        "amount" to transaction.amount,
        "type" to transaction.type,
        "category" to transaction.category,
        "date" to transaction.date,
        "updatedAt" to transaction.updatedAt
    )

    private fun fromMap(data: Map<String, Any?>): Transaction? {
        val id = data["id"] as? String ?: return null
        val description = data["description"] as? String ?: return null
        val amount = (data["amount"] as? Number)?.toDouble() ?: return null
        val type = data["type"] as? String ?: return null
        val date = data["date"] as? String ?: return null
        val category = data["category"] as? String
        val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        return Transaction(description, amount, type, category, date, id, updatedAt)
    }

    // ---- Pending queue --------------------------------------------------------------------

    private fun getPendingOps(context: Context): MutableList<PendingOp> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PENDING_OPS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<PendingOp>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun savePendingOps(context: Context, ops: List<PendingOp>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PENDING_OPS, gson.toJson(ops)).apply()
    }

    /** IDs with a change still waiting to reach Firestore — TransactionRepository must never
     *  trim these from the local cache, since local storage would become their only copy. */
    fun pendingIds(context: Context): Set<String> = getPendingOps(context).map { it.id }.toSet()

    // ---- Entry points called by TransactionRepository -----------------------------------------

    fun queueAdd(context: Context, transaction: Transaction) {
        if (uid() == null) return
        val ops = getPendingOps(context)
        ops.removeAll { it.id == transaction.id }
        val op = PendingOp(transaction.id, OP_ADD, transaction)
        ops.add(op)
        savePendingOps(context, ops)
        pushOne(context, op)
    }

    fun queueDelete(context: Context, transactionId: String) {
        if (uid() == null) return
        val ops = getPendingOps(context)
        ops.removeAll { it.id == transactionId }
        val op = PendingOp(transactionId, OP_DELETE, null)
        ops.add(op)
        savePendingOps(context, ops)
        pushOne(context, op)
    }

    private fun pushOne(context: Context, op: PendingOp) {
        val currentUid = uid() ?: return
        val collection = transactionsCollection(currentUid)
        val task = if (op.operation == OP_ADD) {
            collection.document(op.id).set(toMap(op.transaction!!))
        } else {
            collection.document(op.id).delete()
        }
        task.addOnSuccessListener {
            val remaining = getPendingOps(context)
            remaining.removeAll { it.id == op.id }
            savePendingOps(context, remaining)
            TransactionRepository.trimToLocalCacheLimit(context)
        }
        // On failure the op simply stays queued (it was added before this call); flushPendingOps
        // retries it once connectivity returns. A set()/delete() replay is harmless either way.
    }

    /** Replays every queued operation. Safe to call any time — each op is idempotent. */
    fun flushPendingOps(context: Context) {
        if (uid() == null) return
        for (op in getPendingOps(context)) {
            pushOne(context, op)
        }
    }

    // ---- Connectivity ---------------------------------------------------------------------------

    /** Registers a process-wide listener that flushes the pending queue as soon as the device
     *  comes back online. Safe to call multiple times — only the first registration takes effect. */
    fun registerConnectivityListener(context: Context) {
        if (networkCallback != null) return
        val appContext = context.applicationContext
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                flushPendingOps(appContext)
            }
        }
        networkCallback = callback
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    // ---- Login/logout lifecycle -----------------------------------------------------------------

    /** Pulls this account's most recent transactions from Firestore into the local cache — call
     *  right after a successful sign-in so a returning user's data reappears on a new device or
     *  after a reinstall. A brand-new signup simply gets an empty result back. */
    fun pullInitialData(context: Context, currentUid: String, onComplete: () -> Unit) {
        transactionsCollection(currentUid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(TransactionRepository.LOCAL_CACHE_LIMIT.toLong())
            .get()
            .addOnSuccessListener { snapshot ->
                val transactions = snapshot.documents.mapNotNull { doc -> doc.data?.let { fromMap(it) } }
                TransactionRepository.replaceAll(context, transactions)
                onComplete()
            }
            .addOnFailureListener { onComplete() }
    }

    /** Wipes local transaction data and the pending queue — used on logout, so the next account
     *  signed into this device doesn't start out seeing a previous user's cached data, and before
     *  a fresh signup/login pull so stale pre-auth data doesn't linger. */
    fun clearLocalData(context: Context) {
        TransactionRepository.replaceAll(context, emptyList())
        savePendingOps(context, emptyList())
    }
}
