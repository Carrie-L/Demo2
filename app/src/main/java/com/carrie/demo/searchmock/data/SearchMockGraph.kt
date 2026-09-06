package com.carrie.demo.searchmock.data

import android.content.Context

/** Mock 搜索模块的极简依赖入口，保证全进程共用同一个数据库对象。 */
object SearchMockGraph {
    /** 双重检查单例需要 volatile 保证多线程可见性。 */
    @Volatile
    private var database: SearchHintDatabase? = null

    /** 对外只暴露 DAO，不让 widget 直接操作数据库实现。 */
    fun hintDao(context: Context): SearchHintDao = database(context).hintDao

    /** 延迟创建数据库并持有 Application Context。 */
    private fun database(context: Context): SearchHintDatabase {
        return database ?: synchronized(this) {
            database ?: SearchHintDatabase(context.applicationContext).also { database = it }
        }
    }
}
