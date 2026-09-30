package com.example.footballannotator.model

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
        var x1: Float,
        var y1: Float,
        var x2: Float,
        var y2: Float,
        val style: ArrowStyle = ArrowStyle.SOLID,
        override val color: Int,
        override val strokeWidth: Float
    ) : Annotation(color, strokeWidth)

    data class Line(
        var x1: Float,
        var y1: Float,
        var x2: Float,
        var y2: Float,
        val style: LineStyle = LineStyle.SOLID,
        override val color: Int,
        override val strokeWidth: Float
    ) : Annotation(color, strokeWidth)
}
