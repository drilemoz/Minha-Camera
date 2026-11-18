package com.minhacamera

import kotlin.math.min
import kotlin.math.roundToInt

/** Fit the selected half completely; only the other half is outside the viewport. */
object VideoGeometry {
    data class Frame(val width: Int, val clipHeight: Int, val videoHeight: Int, val videoTop: Float)
    fun displayedAspect(videoAspect: Double, singleLens: Boolean): Double? {
        val aspect = videoAspect * (if (singleLens) 2 else 1)
        return aspect.takeIf { it.isFinite() && it > 0 }
    }

    fun fitFull(viewWidth: Int, viewHeight: Int, videoAspect: Double): Frame? =
        fit(viewWidth, viewHeight, videoAspect, singleLens = false, lower = false)

    fun fitHalf(viewWidth: Int, viewHeight: Int, videoAspect: Double, lower: Boolean): Frame? =
        fit(viewWidth, viewHeight, videoAspect, singleLens = true, lower = lower)

    private fun fit(viewWidth: Int, viewHeight: Int, videoAspect: Double, singleLens: Boolean, lower: Boolean): Frame? {
        if (viewWidth <= 0 || viewHeight <= 0) return null
        val selectedAspect = displayedAspect(videoAspect, singleLens) ?: return null
        val exactHeight = min(viewHeight.toDouble(), viewWidth / selectedAspect)
        val selectedHeight = exactHeight.roundToInt().coerceAtLeast(1)
        val width = (exactHeight * selectedAspect).roundToInt().coerceIn(1, viewWidth)
        val height = selectedHeight * if (singleLens) 2 else 1
        return Frame(width, selectedHeight, height, if (lower) -selectedHeight.toFloat() else 0f)
    }
}
