package com.omerceren.barkodu
import org.junit.Test
import org.junit.Assert.*
import com.omerceren.barkodu.ScanGate.Decision.*

class ScanGateTest {
    @Test fun heldCodeNeverRepeats() {
        val gate=ScanGate();assertEquals(ADD,gate.observe("731234",0));for(t in 16..20000 step 16)assertEquals(IGNORE,gate.observe("731234",t.toLong()))
    }
    @Test fun shakeAndSparseMissesDoNotRearm() {
        val gate=ScanGate();gate.observe("731234",0)
        gate.observe(null,100);gate.observe(null,900);assertEquals(IGNORE,gate.observe("731234",950))
        gate.observe(null,1000);gate.observe(null,5000);assertEquals(IGNORE,gate.observe("731234",5001))
    }
    @Test fun sustainedConfirmedAbsenceRearmsOnce() {
        val gate=ScanGate();gate.observe("731234",0)
        for(t in 100..1600 step 250)gate.observe(null,t.toLong())
        assertEquals(ADD,gate.observe("731234",1700));assertEquals(IGNORE,gate.observe("731234",1800))
    }
    @Test fun differentConsistentCodesNeedNoAbsence() {val gate=ScanGate();assertEquals(ADD,gate.observe("731234",0));assertEquals(ADD,gate.observe("739876",1))}
    @Test fun preferencesDoNotChangeIdentity() {val gate=ScanGate();gate.observe("731234",0,false);assertEquals(IGNORE,gate.observe("731234",800,true))}
    @Test fun pauseIsNotAnExit() {val gate=ScanGate();gate.observe("731234",0);gate.observe(null,100);gate.pause();gate.observe(null,5000);assertEquals(IGNORE,gate.observe("731234",5100))}
    @Test fun consensusRejectsPartialAndAlternatingReads() {
        val consensus=DecodeConsensus();assertNull(consensus.observe("41612157278",0));assertNull(consensus.observe("4161215728",60))
        assertNull(consensus.observe("41612157278",120));assertNull(consensus.observe("4161215728",180));assertNull(consensus.observe("4161215728",240))
        assertEquals("4161215728",consensus.observe("4161215728",300))
    }
    @Test fun delayDoesNotResetConsensusOrGate() {
        val throttle=ScanThrottle();assertTrue(throttle.shouldProcess(0,500));assertFalse(throttle.shouldProcess(499,500));assertTrue(throttle.shouldProcess(500,500))
        val gate=ScanGate();gate.observe("731234",0);assertEquals(IGNORE,gate.observe("731234",500))
        assertTrue(throttle.shouldProcess(501,0));assertEquals(IGNORE,gate.observe("731234",501))
    }
}
