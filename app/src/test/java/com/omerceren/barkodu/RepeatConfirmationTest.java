package com.omerceren.barkodu;
import org.junit.Test;
import static org.junit.Assert.*;
public class RepeatConfirmationTest {
    @Test public void onlyOneEventAndOneAction(){
        RepeatConfirmation flow=new RepeatConfirmation();assertTrue(flow.offer("731234",2));
        long id=flow.get().id;assertFalse(flow.offer("739876",2));
        assertEquals("731234",flow.consume(id).code);assertNull(flow.consume(id));
        assertTrue(flow.offer("731234",2));assertTrue(flow.get().id>id);
    }
    @Test public void staleActionCannotConsumeNewWarning(){
        RepeatConfirmation flow=new RepeatConfirmation();flow.offer("731234",2);long old=flow.get().id;
        flow.dismiss();flow.offer("739876",2);assertNull(flow.consume(old));assertNotNull(flow.get());
    }
    @Test public void carrierIsCapturedWithEvent(){
        RepeatConfirmation flow=new RepeatConfirmation();flow.offer("416123",0);CargoModel model=new CargoModel();model.selected=1;
        RepeatConfirmation.Event event=flow.consume(flow.get().id);model.addForCarrier(event.code,event.carrier);
        assertEquals(0,model.items.get(0).cargo);
    }
}
