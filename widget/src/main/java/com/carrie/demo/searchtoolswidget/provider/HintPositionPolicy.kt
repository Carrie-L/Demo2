package com.carrie.demo.searchtoolswidget.provider

object HintPositionPolicy {
    fun next(clickedPosition: Int, poolSize: Int): Int? {
        if (poolSize <= 0) return null
        if (clickedPosition !in 0 until poolSize) return 0
        return (clickedPosition + 1) % poolSize
    }
}
