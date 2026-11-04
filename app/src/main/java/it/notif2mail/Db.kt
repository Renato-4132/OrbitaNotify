package it.notif2mail

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Due tabelle: le notifiche in attesa di invio (si svuotano appena partono)
 * e la cronologia degli invii.
 */
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "notif2mail.db", null, 1) {

    data class Pending(
        val id: Long,
        val ruleId: String,
        val ts: Long,
        val app: String,
        val pkg: String,
        val title: String,
        val text: String
    )

    data class Hist(
        val ts: Long,
        val rule: String,
        val subject: String,
        val ok: Boolean,
        val detail: String
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE pending(id INTEGER PRIMARY KEY AUTOINCREMENT, rule_id TEXT NOT NULL, " +
                "ts INTEGER NOT NULL, app TEXT, pkg TEXT, title TEXT, text TEXT)"
        )
        db.execSQL(
            "CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, " +
                "rule_name TEXT, subject TEXT, ok INTEGER NOT NULL, detail TEXT)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun addPending(ruleId: String, ts: Long, app: String, pkg: String, title: String, text: String) {
        val v = ContentValues().apply {
            put("rule_id", ruleId)
            put("ts", ts)
            put("app", app)
            put("pkg", pkg)
            put("title", title)
            put("text", text)
        }
        writableDatabase.insert("pending", null, v)
    }

    fun pending(ruleId: String): List<Pending> {
        val out = mutableListOf<Pending>()
        readableDatabase.rawQuery(
            "SELECT id, rule_id, ts, app, pkg, title, text FROM pending WHERE rule_id = ? ORDER BY id",
            arrayOf(ruleId)
        ).use { c ->
            while (c.moveToNext()) {
                out += Pending(
                    c.getLong(0), c.getString(1), c.getLong(2),
                    c.getString(3).orEmpty(), c.getString(4).orEmpty(),
                    c.getString(5).orEmpty(), c.getString(6).orEmpty()
                )
            }
        }
        return out
    }

    fun deletePending(ids: List<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { db.delete("pending", "id = ?", arrayOf(it.toString())) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deletePendingForRule(ruleId: String) {
        writableDatabase.delete("pending", "rule_id = ?", arrayOf(ruleId))
    }

    fun addHistory(ts: Long, ruleName: String, subject: String, ok: Boolean, detail: String) {
        val db = writableDatabase
        val v = ContentValues().apply {
            put("ts", ts)
            put("rule_name", ruleName)
            put("subject", subject)
            put("ok", if (ok) 1 else 0)
            put("detail", detail)
        }
        db.insert("history", null, v)
        db.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY id DESC LIMIT 200)")
    }

    fun history(limit: Int): List<Hist> {
        val out = mutableListOf<Hist>()
        readableDatabase.rawQuery(
            "SELECT ts, rule_name, subject, ok, detail FROM history ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += Hist(
                    c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(),
                    c.getInt(3) == 1, c.getString(4).orEmpty()
                )
            }
        }
        return out
    }

    fun clearHistory() {
        writableDatabase.delete("history", null, null)
    }

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(ctx: Context): Db = instance ?: synchronized(this) {
            instance ?: Db(ctx.applicationContext).also { instance = it }
        }
    }
}
