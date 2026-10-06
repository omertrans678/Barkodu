package com.omerceren.barkodu;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.omerceren.barkodu.ScanGate.Decision.*;

public class ScanGateTest {
    @Test public void heldCodeNeverRepeatsOrCreatesWarning(){
        ScanGate gate=new ScanGate();assertEquals(ADD,gate.observe("731234",0,true));
        for(int t=16;t<20000;t+=16)assertEquals(IGNORE,gate.observe("731234",t,true));
    }
    @Test public void shortAbsenceAndSingleMissDoNotRearm(){
        ScanGate gate=new ScanGate();gate.observe("731234",0,false);
        gate.observe(null,100,false);gate.observe(null,300,false);assertEquals(IGNORE,gate.observe("731234",350,false));
        gate.observe(null,400,false);assertEquals(IGNORE,gate.observe("731234",2000,false));
    }
    @Test public void confirmedExitCreatesFreshEvent(){
        ScanGate gate=new ScanGate();gate.observe("731234",0,true);
        gate.observe(null,100,true);gate.observe(null,600,true);
        assertEquals(ADD,gate.observe("731234",601,true));assertEquals(IGNORE,gate.observe("731234",1401,true));
    }
    @Test public void differentCodesNeverWait(){
        ScanGate gate=new ScanGate();assertEquals(ADD,gate.observe("731234",0,true));
        assertEquals(ADD,gate.observe("739876",1,true));assertEquals(ADD,gate.observe("731234",2,true));
    }
    @Test public void disabledWarningStillLocksCode(){
        ScanGate gate=new ScanGate();gate.observe("731234",0,false);
        assertEquals(IGNORE,gate.observe("731234",800,false));assertEquals(IGNORE,gate.observe("731234",801,true));
    }
    @Test public void pauseDoesNotUnlockHeldBarcode(){
        ScanGate gate=new ScanGate();gate.observe("731234",0,false);gate.observe(null,100,false);gate.pause();
        gate.observe(null,2000,false);assertEquals(IGNORE,gate.observe("731234",2100,false));
    }
}
