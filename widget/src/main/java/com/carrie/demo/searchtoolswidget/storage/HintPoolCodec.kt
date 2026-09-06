package com.carrie.demo.searchtoolswidget.storage

import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** 把完整暗词列表作为一个 JSON 值写入/读出同一个 MMKV key。 */
class HintPoolCodec(
    private val gson: Gson = Gson(),
) {
    /** Gson 反序列化 List<WidgetHintRecord> 所需的泛型类型信息。 */
    private val recordListType = object : TypeToken<List<WidgetHintRecord>>() {}.type

    /** 保留元素所有字段和原始顺序，将整池编码成一个字符串。 */
    fun encode(records: List<WidgetHintRecord>): String = gson.toJson(records, recordListType)

    /** key 不存在、内容为空或 JSON 损坏时安全返回空池，避免 Provider 进程崩溃。 */
    fun decodeOrEmpty(json: String?): List<WidgetHintRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<WidgetHintRecord>>(json, recordListType).orEmpty()
        }.getOrDefault(emptyList())
    }
}
