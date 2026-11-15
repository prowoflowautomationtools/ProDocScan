package com.example.prowoflowdocscan.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImageAdjustmentHelper {
    /**
     * Adjusts the brightness and contrast of a Bitmap.
     * @param source The original Bitmap.
     * @param brightness The brightness value. 0f is default, >0 increases, <0 decreases. Typically -255f to 255f.
     * @param contrast The contrast value. 1f is default, >1f increases, <1f decreases.
     */
    fun adjustBitmap(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        // Contrast scaling matrix
        val contrastMatrix = ColorMatrix().apply {
            val scale = contrast
            val translate = (-.5f * scale + .5f) * 255f
            set(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }

        // Brightness matrix
        val brightnessMatrix = ColorMatrix().apply {
            set(
                floatArrayOf(
                    1f, 0f, 0f, 0f, brightness,
                    0f, 1f, 0f, 0f, brightness,
                    0f, 0f, 1f, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }

        // Combine both matrices
        val finalMatrix = ColorMatrix()
        finalMatrix.setConcat(contrastMatrix, brightnessMatrix)

        val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(finalMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)

        return result
    }
}
