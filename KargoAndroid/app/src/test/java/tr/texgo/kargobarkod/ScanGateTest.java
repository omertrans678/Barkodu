package tr.texgo.kargobarkod;
import org.junit.Test;
import static org.junit.Assert.*;
import static tr.texgo.kargobarkod.ScanGate.Decision.*;

public class ScanGateTest {
    @Test public void continuousBarcodeIsAddedOnceAndWarnedOnce(){
        ScanGate gate=new ScanGate();assertEquals(ADD,gate.observe("7312345678",0,true));int warnings=0;
        for(int time=16;time<=20000;time+=16){ScanGate.Decision result=gate.observe("7312345678",time,true);assertNotEquals(ADD,result);if(result==WARN)warnings++;}
        assertEquals(1,warnings);
    }
    @Test public void shortBlurAndOneSlowMissDoNotRearm(){
        ScanGate gate=new ScanGate();gate.observe("7312345678",0,false);
        gate.observe(null,100,false);gate.observe(null,200,false);gate.observe(null,300,false);
        assertEquals(IGNORE,gate.observe("7312345678",350,false));
        gate.observe(null,400,false);assertEquals(IGNORE,gate.observe("7312345678",2000,false));
    }
    @Test public void confirmedExitAllowsSameBarcodeAsSecondOrder(){
        ScanGate gate=new ScanGate();CargoModel model=new CargoModel();model.selected=2;
        if(gate.observe("7312345678",0,true)==ADD)model.add("7312345678");
        gate.observe(null,100,true);gate.observe(null,600,true);
        assertEquals(ADD,gate.observe("7312345678",601,true));model.add("7312345678");
        assertEquals(2,model.items.get(0).quantity);assertEquals(1,model.items.size());
    }
    @Test public void differentCodesNeverWait(){
        ScanGate gate=new ScanGate();assertEquals(ADD,gate.observe("7312345678",0,true));
        assertEquals(ADD,gate.observe("7398765432",1,true));assertEquals(ADD,gate.observe("7312345678",2,true));
    }
    @Test public void disablingWarningKeepsSuppression(){
        ScanGate gate=new ScanGate();gate.observe("7312345678",0,false);
        assertEquals(IGNORE,gate.observe("7312345678",800,false));
        assertEquals(WARN,gate.observe("7312345678",801,true));
        assertEquals(IGNORE,gate.observe("7312345678",900,false));assertEquals(IGNORE,gate.observe("7312345678",1000,true));
    }
    @Test public void pauseDoesNotUnlockHeldBarcode(){
        ScanGate gate=new ScanGate();gate.observe("7312345678",0,false);gate.observe(null,100,false);gate.pause();
        gate.observe(null,2000,false);assertEquals(IGNORE,gate.observe("7312345678",2100,false));
    }
}
