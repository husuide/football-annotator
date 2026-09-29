package com.example.footballannotator

import com.example.footballannotator.model.Annotation

/**
 * 持有全部标注。
 * 仅实时保存在内存中，进程被杀或浮窗关闭即清除（按需求本期不落盘）。
 * 撤销 = 移除最近一笔。
 */
class AnnotationState {
    val annotations = mutableListOf<Annotation>()

    fun add(a: Annotation) {
        annotations.add(a)
    }

    fun undo(): Boolean {
        return annotations.removeLastOrNull() != null
    }

    fun clearAll() {
        annotations.clear()
    }

    fun removeAt(index: Int): Boolean {
        if (index in annotations.indices) {
            annotations.removeAt(index)
            return true
        }
        return false
    }
}
