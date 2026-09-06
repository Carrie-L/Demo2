package com.carrie.demo.searchmock.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 用 SQLite 模拟正式项目中主搜索模块“网络结果已落数据库”的场景。
 * widget 不拥有这张表，只通过主搜索模块暴露的 [SearchHintDao] 读取。
 */
class SearchHintDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    /** 数据库内部唯一的 DAO 实例。 */
    val hintDao: SearchHintDao = SqliteSearchHintDao(this)

    /** 首次建库时创建最小暗词表。 */
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

    /** Demo 没有迁移需求，版本变化时直接重建；正式项目必须替换为真实迁移脚本。 */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HINTS")
        onCreate(db)
    }

    /** SearchHintDao 的 SQLite 实现。 */
    private class SqliteSearchHintDao(
        private val database: SearchHintDatabase,
    ) : SearchHintDao {

        /**
         * 搜索页面与 Worker 共用同一 SQL；SQL 已确定顺序，调用方不再排序或去重。
         */
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

        /**
         * 在同一事务里删除旧行并写入完整新列表，模拟主搜索网络模块的一次落库。
         * 这是 Mock 数据库内部行为，与 widget 的 MMKV 整体覆盖是两个独立边界。
         */
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
        /** Mock 数据库文件名。 */
        private const val DATABASE_NAME = "search_mock.db"
        /** 当前 Mock schema 版本。 */
        private const val DATABASE_VERSION = 1
        /** 暗词表名。 */
        private const val TABLE_HINTS = "search_hints"
        /** 主键列。 */
        private const val COLUMN_ID = "id"
        /** 暗词文案列。 */
        private const val COLUMN_KEYWORD = "keyword"
        /** 搜索模块排序列。 */
        private const val COLUMN_SORT_ORDER = "sort_order"

        /** 搜索页和 Worker 唯一共用的查询语句。 */
        const val QUERY_HINTS =
            "SELECT id, keyword, sort_order FROM search_hints ORDER BY sort_order ASC, id ASC"
    }
}
