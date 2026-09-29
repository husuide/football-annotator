package com.example.footballannotator.model

import android.graphics.Color

sealed class Annotation(
    open val color: Int,
    open val strokeWidth: Float
) {
    data class Circle(
        val cx: Float,
        val cy: Float,
        var radius: Float,
        override val color: Int,
        override val strokeWidth: Float
    ) : Annotation(color, strokeWidth)

    data class Arrow(
        val x1: Float,
        val y1: Float,
        var x2: Float,
        var y2: Float,
        override val color: Int,
        override val strokeWidth: Float
    ) : Annotation(color, strokeWidth)

    data class Line(
        val x1: Float,
        val y1: Float,
        var x2: Float,
        var y2: Float,
        override val color: Int,
        override val strokeWidth: Float
    ) : Annotation(color, strokeWidth)
}
