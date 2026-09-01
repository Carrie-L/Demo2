package com.carrie.demo.searchmock.data

import android.content.Context

object SearchMockGraph {
    @Volatile
    private var database: SearchHintDatabase? = null

    fun hintDao(context: Context): SearchHintDao = database(context).hintDao

    private fun database(context: Context): SearchHintDatabase {
        return database ?: synchronized(this) {
            database ?: SearchHintDatabase(context.applicationContext).also { database = it }
        }
    }
}

