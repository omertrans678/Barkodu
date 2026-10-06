package com.omerceren.barkodu;

import android.graphics.Bitmap;
import android.graphics.Color;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class FrameBlurTest {
    @Test public void blursRealPixelsAndPreservesOriginalFrame() {
        Bitmap original=Bitmap.createBitmap(64,36,Bitmap.Config.ARGB_8888);
        for(int y=0;y<36;y++)for(int x=0;x<64;x++)original.setPixel(x,y,x<32?Color.BLACK:Color.WHITE);
        Bitmap blurred=FrameBlur.create(original);
        int mid=Color.red(blurred.getPixel(31,18));
        assertTrue(mid>30&&mid<225);
        assertEquals(Color.BLACK,original.getPixel(31,18));
        assertEquals(64,blurred.getWidth());assertEquals(36,blurred.getHeight());
    }
    @Test public void largeFramesAreBoundedAndSolidColorsArePreserved() {
        Bitmap original=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);original.eraseColor(Color.BLUE);
        Bitmap blurred=FrameBlur.create(original);
        assertEquals(320,blurred.getWidth());assertEquals(180,blurred.getHeight());
        assertEquals(Color.BLUE,blurred.getPixel(160,90));
    }
}
