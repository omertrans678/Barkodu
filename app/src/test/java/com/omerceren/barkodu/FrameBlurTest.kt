package com.omerceren.barkodu

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class FrameBlurTest {
    @Test fun blursPixelsWithoutMutatingInput() {
        val original=Bitmap.createBitmap(64,36,Bitmap.Config.ARGB_8888)
        for(y in 0 until 36)for(x in 0 until 64)original.setPixel(x,y,if(x<32)Color.BLACK else Color.WHITE)
        val blurred=FrameBlur.create(original)
        assertTrue(Color.red(blurred.getPixel(31,18)) in 31..224)
        assertEquals(Color.BLACK,original.getPixel(31,18));assertEquals(64,blurred.width);assertEquals(36,blurred.height)
    }
    @Test fun boundedSolidFrames() {
        val original=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888).also {it.eraseColor(Color.BLUE)}
        val blurred=FrameBlur.create(original)
        assertEquals(320,blurred.width);assertEquals(180,blurred.height);assertEquals(Color.BLUE,blurred.getPixel(160,90))
    }
}
