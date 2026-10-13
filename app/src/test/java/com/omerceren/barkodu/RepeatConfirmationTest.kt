package com.omerceren.barkodu
import org.junit.Test
import org.junit.Assert.*

class RepeatConfirmationTest {
    @Test fun onlyOneEventAndAction() {
        val flow=RepeatConfirmation();assertTrue(flow.offer("731234",2));val id=flow.get()!!.id
        assertFalse(flow.offer("739876",2));assertEquals("731234",flow.consume(id)!!.code);assertNull(flow.consume(id))
        assertTrue(flow.offer("731234",2));assertTrue(flow.get()!!.id>id)
    }
    @Test fun staleActionCannotConsumeNewWarning() {
        val flow=RepeatConfirmation();flow.offer("731234",2);val id=flow.get()!!.id
        flow.dismiss();flow.offer("739876",2);assertNull(flow.consume(id));assertNotNull(flow.get())
    }
    @Test fun capturedCarrier() {
        val flow=RepeatConfirmation();flow.offer("416123",0);val model=CargoModel();model.selected=1
        val event=flow.consume(flow.get()!!.id)!!;model.addForCarrier(event.code,event.carrier);assertEquals(0,model.items.first().cargo)
    }
}
