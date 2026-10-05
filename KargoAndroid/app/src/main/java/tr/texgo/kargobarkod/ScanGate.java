package tr.texgo.kargobarkod;

/** Suppresses repeated detections of a barcode held in view, not repeat orders. */
public final class ScanGate {
    public enum Decision { ADD, IGNORE, WARN }
    private static final long ABSENCE_MS=500, WARNING_AFTER_MS=800;
    private String lockedCode="";
    private long missingSince=-1, acceptedAt=0;
    private boolean warned=false;

    public Decision observe(String code,long now,boolean warningEnabled){
        if(code==null){
            if(missingSince<0)missingSince=now;
            else if(now-missingSince>=ABSENCE_MS){lockedCode="";warned=false;}
            return Decision.IGNORE;
        }
        missingSince=-1;
        if(!code.equals(lockedCode)){
            lockedCode=code;acceptedAt=now;warned=false;
            return Decision.ADD;
        }
        if(warningEnabled&&!warned&&now-acceptedAt>=WARNING_AFTER_MS){
            warned=true;return Decision.WARN;
        }
        return Decision.IGNORE;
    }
    /** Pausing the camera is not evidence that a package left its view. */
    public void pause(){missingSince=-1;}
}
