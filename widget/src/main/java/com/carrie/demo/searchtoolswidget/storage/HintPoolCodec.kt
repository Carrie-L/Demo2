package com.carrie.demo.searchtoolswidget.storage

import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class HintPoolCodec(
    private val gson: Gson = Gson(),
) {
    private val recordListType = object : TypeToken<List<WidgetHintRecord>>() {}.type

    fun encode(records: List<WidgetHintRecord>): String = gson.toJson(records, recordListType)

    fun decodeOrEmpty(json: String?): List<WidgetHintRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<WidgetHintRecord>>(json, recordListType).orEmpty()
        }.getOrDefault(emptyList())
    }
}

