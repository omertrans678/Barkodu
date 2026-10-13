package com.omerceren.barkodu

import android.graphics.Bitmap
import kotlin.math.roundToInt

object FrameBlur {
    fun create(source: Bitmap): Bitmap {
        val width = minOf(320, source.width)
        val height = maxOf(1, (source.height * (width / source.width.toFloat())).roundToInt())
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        val pixels = IntArray(width * height); val scratch = IntArray(pixels.size)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        repeat(3) {blur(pixels, scratch, width, height, 8, true); blur(scratch, pixels, width, height, 8, false)}
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {it.setPixels(pixels, 0, width, 0, 0, width, height)}
    }
    private fun blur(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if(horizontal) height else width; val length = if(horizontal) width else height; val window = radius * 2 + 1
        fun index(line: Int, position: Int) = if(horizontal) line * width + position else position * width + line
        for(line in 0 until lines) {
            var r = 0; var g = 0; var b = 0
            for(n in -radius..radius) {val p = input[index(line, n.coerceIn(0, length - 1))]; r += p shr 16 and 255; g += p shr 8 and 255; b += p and 255}
            for(n in 0 until length) {
                output[index(line, n)] = 0xff000000.toInt() or ((r/window) shl 16) or ((g/window) shl 8) or (b/window)
                val leaving = input[index(line, (n-radius).coerceAtLeast(0))]; val entering = input[index(line, (n+radius+1).coerceAtMost(length-1))]
                r += (entering shr 16 and 255) - (leaving shr 16 and 255)
                g += (entering shr 8 and 255) - (leaving shr 8 and 255)
                b += (entering and 255) - (leaving and 255)
            }
        }
    }
}
