package it;

import cern.c2mon.daq.common.messaging.IProcessMessageSender;
import cern.c2mon.daq.opcua.OPCUAMessageHandler;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.test.GenericMessageHandlerTest;
import cern.c2mon.daq.test.UseConf;
import cern.c2mon.daq.test.UseHandler;
import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import org.easymock.Capture;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static cern.c2mon.daq.opcua.exceptions.ConfigurationException.Cause.ADDRESS_MISSING_PROPERTIES;
import static cern.c2mon.daq.opcua.upstream.EquipmentStateListener.EquipmentState.CONNECTION_FAILED;
import static cern.c2mon.daq.opcua.upstream.EquipmentStateListener.EquipmentState.OK;
import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@UseHandler(OPCUAMessageHandler.class)
public class OPCUAMessageHandlerTest extends GenericMessageHandlerTest {

    OPCUAMessageHandler handler;

    @BeforeClass
    public static void setupServer () throws Exception {
        OpcUaInfrastructureBase.setupServer();
    }

    @AfterClass
    public static void tearDownServer () throws Exception {
        OpcUaInfrastructureBase.tearDownServer();
    }

    @Override
    protected void beforeTest () throws Exception {
        handler = (OPCUAMessageHandler) msgHandler;
    }

    @Override
    protected void afterTest () throws Exception {
//        handler.disconnectFromDataSource();
    }

    @Test
    @UseConf("commfault_ok.xml")
    public void properConfigShouldSendOKCommfault () throws EqIOException, InterruptedException {
        CommfaultSenderCapture capture = new CommfaultSenderCapture(messageSender);

        handler.connectToDataSource();
        Thread.sleep(2000);

        capture.verifyCapture(107211L,
                handler.getEquipmentConfiguration().getName()+":COMM_FAULT",
                OK.message);
    }

    @Test
    @UseConf("bad_address_string.xml")
    public void badAddressStringShouldThrowError () {
        assertThrows(ConfigurationException.class, () -> handler.connectToDataSource(), ADDRESS_MISSING_PROPERTIES.message);
    }

    @Test
    @UseConf("commfault_incorrect.xml")
    public void improperConfigShouldSendIncorrectCommfault () {
        CommfaultSenderCapture capture = new CommfaultSenderCapture(messageSender);

        try {
            handler.connectToDataSource();
            Thread.sleep(2000);
        }
        catch (OPCCommunicationException | EqIOException | InterruptedException ignored) {}

        capture.verifyCapture(107211L,
                handler.getEquipmentConfiguration().getName()+":COMM_FAULT",
                CONNECTION_FAILED.message);

    }

    @Test
    @UseConf("commfault_incorrect.xml")
    public void improperConfigShouldSendThrowError () {
        new CommfaultSenderCapture(messageSender);
        assertThrows(ConfigurationException.class, () -> handler.connectToDataSource());
    }

    @Test
    @UseConf("empty_datatags.xml")
    public void emptyDatatagsShouldThrowError () {
        assertThrows(ConfigurationException.class, () -> handler.connectToDataSource());
    }

    private static class CommfaultSenderCapture {
        Capture<Long> id = newCapture();
        Capture<String> tagName = newCapture();
        Capture<Boolean> val = newCapture();
        Capture<String> msg= newCapture();

        CommfaultSenderCapture (IProcessMessageSender sender) {
            sender.sendCommfaultTag(captureLong(id), capture(tagName), captureBoolean(val), capture(msg));
            expectLastCall().once();
            replay(sender);
        }

        void verifyCapture(Long id, String tagName, String msg) {
            assertEquals(id, this.id.getValue().longValue());
            assertEquals(tagName, this.tagName.getValue());
            assertEquals(msg, this.msg.getValue());
        }
    }


}
