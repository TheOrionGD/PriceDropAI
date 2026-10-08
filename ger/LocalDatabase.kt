package com.example.pricedropai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class WatchlistItem(
    val id: Long,
    val productName: String,
    val targetPrice: Double
)

class LocalDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "pricedrop.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_WATCHLIST = "watchlist"
        private const val COL_ID = "id"
        private const val COL_NAME = "product_name"
        private const val COL_TARGET_PRICE = "target_price"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_WATCHLIST (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT,
                $COL_TARGET_PRICE REAL
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WATCHLIST")
        onCreate(db)
    }

    fun insertWatchlist(name: String, targetPrice: Double): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_NAME, name)
            put(COL_TARGET_PRICE, targetPrice)
        }
        return db.insert(TABLE_WATCHLIST, null, values)
    }

    // Resolves references for both getWatchlist() and getAllWatchlist()
    fun getWatchlist(): List<WatchlistItem> = getAllWatchlist()

    fun getAllWatchlist(): List<WatchlistItem> {
        val list = mutableListOf<WatchlistItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_WATCHLIST", null)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME))
                val price = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_TARGET_PRICE))
                list.add(WatchlistItem(id, name, price))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun deleteWatchlist(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_WATCHLIST, "$COL_ID = ?", arrayOf(id.toString()))
    }
}