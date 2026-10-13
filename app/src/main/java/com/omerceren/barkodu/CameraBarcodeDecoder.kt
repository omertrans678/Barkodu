package com.omerceren.barkodu

import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.common.GlobalHistogramBinarizer

/** Local decoder only: no services, telemetry, downloads, sockets or image uploads. */
class CameraBarcodeDecoder {
    private val stability = FrameStability()
    private var luminance=ByteArray(0)
    var isFrameStable = false
        private set
    private val reader = MultiFormatReader().apply {setHints(mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.ITF, BarcodeFormat.CODABAR),
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.RETURN_CODABAR_START_END to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
        DecodeHintType.ALLOWED_LENGTHS to (6..64 step 2).toList().toIntArray()
    ))}
    fun decode(image: ImageProxy): String? {
        val crop = image.cropRect; val plane = image.planes[0]; val buffer = plane.buffer.duplicate()
        val count=crop.width()*crop.height()
        if(luminance.size!=count)luminance=ByteArray(count)
        val bytes=luminance;val base=buffer.position()
        for(y in 0 until crop.height()) {
            val row=base+(crop.top+y)*plane.rowStride+crop.left*plane.pixelStride
            if(plane.pixelStride==1){buffer.position(row);buffer.get(bytes,y*crop.width(),crop.width())}
            else for(x in 0 until crop.width())bytes[y*crop.width()+x]=buffer.get(row+x*plane.pixelStride)
        }
        return decode(bytes, crop.width(), crop.height())
    }
    fun decode(bytes: ByteArray, width: Int, height: Int): String? {
        isFrameStable = stability.observe(bytes,width,height)
        val source=FrameSource(bytes,width,height)
        // Live camera lighting can defeat local thresholds even when a scan line is readable.
        return read(HybridBinarizer(source)) ?: read(GlobalHistogramBinarizer(source))
    }
    private fun read(binarizer:Binarizer):String? = try {reader.decodeWithState(BinaryBitmap(binarizer)).text}
        catch(notFound:ReaderException){null}
        finally{reader.reset()}
    private class FrameSource(private val bytes: ByteArray, width: Int, height: Int): LuminanceSource(width, height) {
        override fun getRow(y: Int, row: ByteArray?): ByteArray = (row?.takeIf {it.size >= width} ?: ByteArray(width)).also {bytes.copyInto(it, 0, y*width, (y+1)*width)}
        override fun getMatrix() = bytes
        override fun isRotateSupported() = true
        override fun rotateCounterClockwise(): LuminanceSource {
            val rotated = ByteArray(bytes.size)
            for(y in 0 until height) for(x in 0 until width) rotated[(width-x-1)*height+y] = bytes[y*width+x]
            return FrameSource(rotated, height, width)
        }
    }
}

/** Empty decodes while the image changes cannot re-arm presentation identity. No gyro input is used. */
class FrameStability {
    private var previous: IntArray? = null
    fun observe(bytes: ByteArray, width: Int, height: Int): Boolean {
        val samples=IntArray(256) {index-> bytes[(index/16*height/16)*width+(index%16*width/16)].toInt() and 255}
        val old=previous;previous=samples
        return old!=null && samples.indices.sumOf {kotlin.math.abs(samples[it]-old[it])}/256.0 <= 12.0
    }
}
