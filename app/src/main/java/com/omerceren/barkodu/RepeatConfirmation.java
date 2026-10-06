package com.omerceren.barkodu;

/** One pending event, consumed before mutation to make rapid action taps harmless. */
public final class RepeatConfirmation {
    public static final class Event {
        public final long id;
        public final String code;
        public final int carrier;
        Event(long id,String code,int carrier){this.id=id;this.code=code;this.carrier=carrier;}
    }
    private long nextId;
    private Event pending;
    public Event get(){return pending;}
    public boolean offer(String code,int carrier){
        if(pending!=null)return false;
        pending=new Event(++nextId,code,carrier);return true;
    }
    public Event consume(long id){
        if(pending==null||pending.id!=id)return null;
        Event result=pending;pending=null;return result;
    }
    public void dismiss(){pending=null;}
}
