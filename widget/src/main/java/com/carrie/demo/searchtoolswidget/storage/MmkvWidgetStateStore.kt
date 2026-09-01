package com.carrie.demo.searchtoolswidget.storage

import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import com.tencent.mmkv.MMKV

class MmkvWidgetStateStore private constructor(
    private val mmkv: MMKV,
    private val codec: HintPoolCodec,
) : WidgetStateStore {

    override fun readPool(): List<WidgetHintRecord> = codec.decodeOrEmpty(
        mmkv.decodeString(KEY_HINT_POOL),
    )

    override fun replacePool(pool: List<WidgetHintRecord>) {
        check(mmkv.encode(KEY_HINT_POOL, codec.encode(pool))) {
            "Failed to persist widget hint pool"
        }
    }

    override fun clearPool() {
        replacePool(emptyList())
    }

    override fun updateLastSuccessfulRefreshAt(timestampMillis: Long) {
        check(mmkv.encode(KEY_LAST_SUCCESS, timestampMillis)) {
            "Failed to persist widget refresh timestamp"
        }
    }

    override fun lastSuccessfulRefreshAtMillis(): Long = mmkv.decodeLong(KEY_LAST_SUCCESS, 0L)

    override fun setPrivacyAllowed(allowed: Boolean) {
        check(mmkv.encode(KEY_PRIVACY_ALLOWED, allowed)) {
            "Failed to persist widget privacy gate"
        }
    }

    override fun isPrivacyAllowed(): Boolean = mmkv.decodeBool(KEY_PRIVACY_ALLOWED, false)

    override fun setFrequencyMinutes(minutes: Long) {
        check(mmkv.encode(KEY_FREQUENCY_MINUTES, minutes)) {
            "Failed to persist widget frequency"
        }
    }

    override fun frequencyMinutes(): Long = mmkv.decodeLong(KEY_FREQUENCY_MINUTES, 60L)

    companion object {
        private const val MMKV_ID = "search_tools_widget_state"
        private const val KEY_HINT_POOL = "widget_hint_pool_json"
        private const val KEY_LAST_SUCCESS = "widget_last_successful_refresh_at_ms"
        private const val KEY_PRIVACY_ALLOWED = "widget_privacy_allowed"
        private const val KEY_FREQUENCY_MINUTES = "widget_frequency_minutes"

        @Volatile
        private var instance: MmkvWidgetStateStore? = null

        fun get(): MmkvWidgetStateStore = instance ?: synchronized(this) {
            instance ?: MmkvWidgetStateStore(
                mmkv = requireNotNull(MMKV.mmkvWithID(MMKV_ID, MMKV.MULTI_PROCESS_MODE)) {
                    "MMKV must be initialized before WidgetStateStore"
                },
                codec = HintPoolCodec(),
            ).also { instance = it }
        }
    }
}

