package com.omerceren.barkodu;

import android.graphics.Bitmap;

/** Bounded software blur for a paused camera frame, including Android 6–11. */
final class FrameBlur {
    static Bitmap create(Bitmap source) {
        int width=Math.min(320,source.getWidth());
        int height=Math.max(1,Math.round(source.getHeight()*(width/(float)source.getWidth())));
        Bitmap scaled=Bitmap.createScaledBitmap(source,width,height,true);
        int[] pixels=new int[width*height],scratch=new int[pixels.length];
        scaled.getPixels(pixels,0,width,0,0,width,height);
        for(int pass=0;pass<3;pass++) {
            blur(pixels,scratch,width,height,8,true);
            blur(scratch,pixels,width,height,8,false);
        }
        Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        result.setPixels(pixels,0,width,0,0,width,height);
        return result;
    }
    private static void blur(int[] input,int[] output,int width,int height,int radius,boolean horizontal) {
        int lines=horizontal?height:width,length=horizontal?width:height,window=radius*2+1;
        for(int line=0;line<lines;line++) {
            int r=0,g=0,b=0;
            for(int n=-radius;n<=radius;n++) {
                int p=input[index(line,Math.max(0,Math.min(length-1,n)),width,horizontal)];
                r+=(p>>16)&255;g+=(p>>8)&255;b+=p&255;
            }
            for(int n=0;n<length;n++) {
                output[index(line,n,width,horizontal)]=0xff000000|(r/window<<16)|(g/window<<8)|b/window;
                int leaving=input[index(line,Math.max(0,n-radius),width,horizontal)];
                int entering=input[index(line,Math.min(length-1,n+radius+1),width,horizontal)];
                r+=((entering>>16)&255)-((leaving>>16)&255);
                g+=((entering>>8)&255)-((leaving>>8)&255);b+=(entering&255)-(leaving&255);
            }
        }
    }
    private static int index(int line,int position,int width,boolean horizontal){return horizontal?line*width+position:position*width+line;}
}
