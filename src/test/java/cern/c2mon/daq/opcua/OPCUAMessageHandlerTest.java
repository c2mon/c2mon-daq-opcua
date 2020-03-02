//package cern.c2mon.daq.opcua;
//
//import cern.c2mon.daq.test.GenericMessageHandlerTest;
//import cern.c2mon.daq.test.UseConf;
//import cern.c2mon.daq.test.UseHandler;
//import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
//import org.easymock.Capture;
//import org.junit.Test;
//
//import static org.easymock.EasyMock.*;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//
//@UseHandler(OPCUAMessageHandler.class)
//public class OPCUAMessageHandlerTest extends GenericMessageHandlerTest {
//    OPCUAMessageHandler handler;
//
//    @Override
//    protected void beforeTest () throws Exception {
//        handler = (OPCUAMessageHandler) msgHandler;
//    }
//
//    @Override
//    protected void afterTest () throws Exception {
////        handler.disconnectFromDataSource();
//    }
//
//    @Test
//    @UseConf("test1.xml")
//    public void test() throws EqIOException, InterruptedException {
//        // create junit captures for the tag id, value and message (for the commmfault tag)
//        Capture<Long> id = newCapture();
//        Capture<String> tagName = newCapture();
//        Capture<Boolean> val = newCapture();
//        Capture<String> msg = newCapture();
//
//        messageSender.sendCommfaultTag(captureLong(id), capture(tagName), captureBoolean(val), capture(msg));
//        expectLastCall().once();
//
//        // record the mock
//        replay(messageSender);
//
//        handler.
//        handler.connectToDataSource();
//
//        Thread.sleep(2000);
//
//        // verify that messageSender's interfaces were called according to what has been recorded
//        verify(messageSender);
//
//        // check the message of the commfault tag is as expected
//
//        // check the id of the commfault tag is correct
//        assertEquals(107211L, id.getValue().longValue());
//        // ..and the value
//        assertEquals(false, val.getValue());
//    }
//
//}
