package com.omerceren.barkodu;

/** Suppresses repeated detections of a barcode held in view, not repeat orders. */
public final class ScanGate {
    public enum Decision { ADD, IGNORE }
    private static final long ABSENCE_MS=500;
    private String lockedCode="";
    private long missingSince=-1;

    public Decision observe(String code,long now,boolean warningEnabled){
        if(code==null){
            if(missingSince<0)missingSince=now;
            else if(now-missingSince>=ABSENCE_MS){lockedCode="";}
            return Decision.IGNORE;
        }
        missingSince=-1;
        if(!code.equals(lockedCode)){
            lockedCode=code;
            return Decision.ADD;
        }
        // Confirmation belongs to a fresh detection event, never a held camera frame.
        return Decision.IGNORE;
    }
    /** Pausing the camera is not evidence that a package left its view. */
    public void pause(){missingSince=-1;}
}
