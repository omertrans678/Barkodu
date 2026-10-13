package com.omerceren.barkodu
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.junit.Test
import org.junit.Assert.*

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[34])
class CameraBarcodeDecoderTest {
    @Test fun allExistingFormatsDecodeOffline() {
        val examples=mapOf(BarcodeFormat.CODE_128 to "001Ab23456",BarcodeFormat.CODE_39 to "HTS001234",BarcodeFormat.CODE_93 to "73123456",BarcodeFormat.ITF to "4161215728",BarcodeFormat.CODABAR to "A0123456B")
        examples.forEach {(format,text)->
            val image=MultiFormatWriter().encode(text,format,800,240)
            val pixels=ByteArray(image.width*image.height){index->if(image[index%image.width,index/image.width])0 else 255.toByte()}
            assertEquals(format.toString(),text,CameraBarcodeDecoder().decode(pixels,image.width,image.height))
        }
    }
    @Test fun verticalBarcodePreservesZeros() {
        val image=MultiFormatWriter().encode("0041612157",BarcodeFormat.CODE_128,800,240)
        val rotated=ByteArray(image.width*image.height)
        for(y in 0 until image.height)for(x in 0 until image.width)rotated[x*image.height+image.height-y-1]=if(image[x,y])0 else 255.toByte()
        assertEquals("0041612157",CameraBarcodeDecoder().decode(rotated,image.height,image.width))
    }
    @Test fun blankFramesReturnNoPayload() {assertNull(CameraBarcodeDecoder().decode(ByteArray(640*360){255.toByte()},640,360))}
    @Test fun incorrectBusinessLengthIsRejectedRatherThanTruncated() {
        val code="41612157278"
        val image=MultiFormatWriter().encode(code,BarcodeFormat.CODE_128,800,240)
        val pixels=ByteArray(image.width*image.height){i->if(image[i%image.width,i/image.width])0 else 255.toByte()}
        assertEquals(code,CameraBarcodeDecoder().decode(pixels,image.width,image.height))
        assertFalse(CarrierRules().validLength(code,0))
    }
    @Test fun changingEmptyFramesAreNotStableAbsenceEvidence() {
        val stability=FrameStability();val white=ByteArray(640*360){255.toByte()};val black=ByteArray(white.size)
        assertFalse(stability.observe(white,640,360));assertTrue(stability.observe(white,640,360))
        assertFalse(stability.observe(black,640,360));assertTrue(stability.observe(black,640,360))
    }
    @Test fun paddedCroppedStridedCameraPlanePreservesCompletePayload() {
        val label=MultiFormatWriter().encode("4161215728",BarcodeFormat.CODE_128,400,160)
        val stride=828;val offset=7;val pixelStride=2
        val bytes=ByteArray(offset+stride*164)
        for(y in 0 until 160)for(x in 0 until 400)bytes[offset+(y+2)*stride+(x+5)*pixelStride]=if(label[x,y])0 else 255.toByte()
        val buffer=java.nio.ByteBuffer.wrap(bytes).also {it.position(offset)}
        val plane=java.lang.reflect.Proxy.newProxyInstance(androidx.camera.core.ImageProxy.PlaneProxy::class.java.classLoader,arrayOf(androidx.camera.core.ImageProxy.PlaneProxy::class.java)){_,method,_->
            when(method.name){"getBuffer"->buffer;"getRowStride"->stride;"getPixelStride"->pixelStride;else->throw UnsupportedOperationException(method.name)}
        } as androidx.camera.core.ImageProxy.PlaneProxy
        val frame=java.lang.reflect.Proxy.newProxyInstance(androidx.camera.core.ImageProxy::class.java.classLoader,arrayOf(androidx.camera.core.ImageProxy::class.java)){_,method,_->
            when(method.name){"getCropRect"->android.graphics.Rect(5,2,405,162);"getPlanes"->arrayOf(plane);else->throw UnsupportedOperationException(method.name)}
        } as androidx.camera.core.ImageProxy
        assertEquals("4161215728",CameraBarcodeDecoder().decode(frame))
        assertEquals(offset,buffer.position())
    }
}
