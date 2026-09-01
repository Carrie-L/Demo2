package com.carrie.demo.searchmock.data

interface SearchHintDao {
    /** This one query is shared by the mock search page and widget sync worker. */
    fun queryHints(): List<SearchHintRecord>

    fun replaceAll(records: List<SearchHintRecord>)
}

