package com.carrie.demo.searchmock.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SearchHintDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    val hintDao: SearchHintDao = SqliteSearchHintDao(this)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_HINTS (
                $COLUMN_ID INTEGER PRIMARY KEY,
                $COLUMN_KEYWORD TEXT NOT NULL,
                $COLUMN_SORT_ORDER INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HINTS")
        onCreate(db)
    }

    private class SqliteSearchHintDao(
        private val database: SearchHintDatabase,
    ) : SearchHintDao {

        override fun queryHints(): List<SearchHintRecord> {
            return database.readableDatabase.rawQuery(QUERY_HINTS, null).use { cursor ->
                buildList {
                    val idIndex = cursor.getColumnIndexOrThrow(COLUMN_ID)
                    val keywordIndex = cursor.getColumnIndexOrThrow(COLUMN_KEYWORD)
                    val orderIndex = cursor.getColumnIndexOrThrow(COLUMN_SORT_ORDER)
                    while (cursor.moveToNext()) {
                        add(
                            SearchHintRecord(
                                id = cursor.getLong(idIndex),
                                keyword = cursor.getString(keywordIndex),
                                sortOrder = cursor.getInt(orderIndex),
                            ),
                        )
                    }
                }
            }
        }

        override fun replaceAll(records: List<SearchHintRecord>) {
            database.writableDatabase.apply {
                beginTransaction()
                try {
                    delete(TABLE_HINTS, null, null)
                    records.forEach { record ->
                        insertOrThrow(
                            TABLE_HINTS,
                            null,
                            ContentValues().apply {
                                put(COLUMN_ID, record.id)
                                put(COLUMN_KEYWORD, record.keyword)
                                put(COLUMN_SORT_ORDER, record.sortOrder)
                            },
                        )
                    }
                    setTransactionSuccessful()
                } finally {
                    endTransaction()
                }
            }
        }
    }

    companion object {
        private const val DATABASE_NAME = "search_mock.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_HINTS = "search_hints"
        private const val COLUMN_ID = "id"
        private const val COLUMN_KEYWORD = "keyword"
        private const val COLUMN_SORT_ORDER = "sort_order"

        const val QUERY_HINTS =
            "SELECT id, keyword, sort_order FROM search_hints ORDER BY sort_order ASC, id ASC"
    }
}

