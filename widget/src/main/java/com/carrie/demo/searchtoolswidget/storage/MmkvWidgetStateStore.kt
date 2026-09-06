package com.carrie.demo.searchtoolswidget.storage

import com.carrie.demo.searchtoolswidget.model.WidgetHintRecord
import com.tencent.mmkv.MMKV

/**
 * widget 持久状态的 MMKV 实现。
 *
 * 当前 App 与 widget 已统一运行在主进程，因此使用 SINGLE_PROCESS_MODE。若正式项目重新给
 * Provider/Service 配置独立 android:process，必须同步切换到 MMKV.MULTI_PROCESS_MODE。
 */
class MmkvWidgetStateStore private constructor(
    private val mmkv: MMKV,
    private val codec: HintPoolCodec,
) : WidgetStateStore {

    /** 从单个 JSON key 解码完整暗词池。 */
    override fun readPool(): List<WidgetHintRecord> {
        return codec.decodeOrEmpty(mmkv.decodeString(KEY_HINT_POOL))
    }

    /**
     * 对同一个 key 做一次整体覆盖；无需先 delete，避免读者在删除与写入之间读到空窗。
     * encode 返回 false 时抛出异常，让同步引擎把本轮判定为 Failed 并保留既有值。
     */
    override fun replacePool(pool: List<WidgetHintRecord>) {
        check(mmkv.encode(KEY_HINT_POOL, codec.encode(pool))) {
            "Failed to persist widget hint pool"
        }
    }

    /** 产品要求空查询结果成为有效空池，因此清空同样走整体覆盖。 */
    override fun clearPool() {
        replacePool(emptyList())
    }

    /** 写入最近一次数据库读取成功的毫秒时间戳。 */
    override fun updateLastSuccessfulRefreshAt(timestampMillis: Long) {
        check(mmkv.encode(KEY_LAST_SUCCESS, timestampMillis)) {
            "Failed to persist widget refresh timestamp"
        }
    }

    /** 读取最近一次成功时间，供诊断或 UI 展示；它不参与新旧池竞争判断。 */
    override fun lastSuccessfulRefreshAtMillis(): Long {
        return mmkv.decodeLong(KEY_LAST_SUCCESS, 0L)
    }

    /** 保存由主 App SP 同步过来的隐私许可快照。 */
    override fun setPrivacyAllowed(allowed: Boolean) {
        check(mmkv.encode(KEY_PRIVACY_ALLOWED, allowed)) {
            "Failed to persist widget privacy gate"
        }
    }

    /** Provider 渲染前读取的隐私门禁。 */
    override fun isPrivacyAllowed(): Boolean {
        return mmkv.decodeBool(KEY_PRIVACY_ALLOWED, false)
    }

    /** 记录 WorkManager 当前采用的周期，便于配置没变时 KEEP 原计时。 */
    override fun setFrequencyMinutes(minutes: Long) {
        check(mmkv.encode(KEY_FREQUENCY_MINUTES, minutes)) {
            "Failed to persist widget frequency"
        }
    }

    /** 未写入过时使用产品默认的 60 分钟。 */
    override fun frequencyMinutes(): Long {
        return mmkv.decodeLong(KEY_FREQUENCY_MINUTES, 60L)
    }

    companion object {
        /** 本功能独立的 MMKV 文件 id。 */
        private const val MMKV_ID = "search_tools_widget_state"

        /** 整个暗词池的 JSON；列表、顺序和记录字段一次性覆盖。 */
        private const val KEY_HINT_POOL = "widget_hint_pool_json"

        /** 最近一次 DAO 查询成功时间，仅用于状态记录/诊断。 */
        private const val KEY_LAST_SUCCESS = "widget_last_successful_refresh_at_ms"

        /** 主 App 隐私协议状态的 widget 侧快照。 */
        private const val KEY_PRIVACY_ALLOWED = "widget_privacy_allowed"

        /** 已提交给 WorkManager 的周期分钟数。 */
        private const val KEY_FREQUENCY_MINUTES = "widget_frequency_minutes"

        /** 双重检查单例的可见性保证。 */
        @Volatile
        private var instance: MmkvWidgetStateStore? = null

        /**
         * 获取进程内唯一 Store。调用前必须先执行 [WidgetStorageInitializer.initialize]。
         */
        fun get(): MmkvWidgetStateStore = instance ?: synchronized(this) {
            instance ?: MmkvWidgetStateStore(
                mmkv = requireNotNull(MMKV.mmkvWithID(MMKV_ID, MMKV.SINGLE_PROCESS_MODE)) {
                    "MMKV must be initialized before WidgetStateStore"
                },
                codec = HintPoolCodec(),
            ).also { instance = it }
        }
    }
}
