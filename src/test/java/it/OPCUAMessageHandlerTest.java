package it;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.common.messaging.IProcessMessageSender;
import cern.c2mon.daq.opcua.OPCUAMessageHandler;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.upstream.EndpointListener;
import cern.c2mon.daq.test.GenericMessageHandlerTest;
import cern.c2mon.daq.test.UseConf;
import cern.c2mon.daq.test.UseHandler;
import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import it.iotedge.EdgeConnectionResolver;
import lombok.extern.slf4j.Slf4j;
import org.easymock.Capture;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static cern.c2mon.daq.opcua.exceptions.ConfigurationException.Cause.ADDRESS_MISSING_PROPERTIES;
import static cern.c2mon.daq.opcua.upstream.EndpointListener.EquipmentState.CONNECTION_FAILED;
import static cern.c2mon.daq.opcua.upstream.EndpointListener.EquipmentState.OK;
import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@UseHandler(OPCUAMessageHandler.class)
public class OPCUAMessageHandlerTest extends GenericMessageHandlerTest {

    OPCUAMessageHandler handler;
    static EdgeConnectionResolver connectionResolver;

    @BeforeClass
    public static void startServer() throws InterruptedException, TimeoutException, ExecutionException {
        // Since GenericMessageHandlerTest.class uses Junit 4, we cannot use a EdgeConnectionResolver
        // as an extension.
        // TODO: don't extend MessageHandler but use spring boot for DI, migrate all tests to junit 5
        connectionResolver = new EdgeConnectionResolver();
        connectionResolver.initialize();
    }

    @AfterClass
    public static void stopServer() {
        connectionResolver.close();
    }

    @Override
    protected void beforeTest () throws Exception {
        handler = (OPCUAMessageHandler) msgHandler;
    }

    @Override
    protected void afterTest () throws Exception { }


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
        assertThrows(OPCCommunicationException.class, () -> handler.connectToDataSource());
    }

    @Test
    @UseConf("empty_datatags.xml")
    public void emptyDatatagsShouldThrowError () {
        assertThrows(ConfigurationException.class, () -> handler.connectToDataSource());
    }


    EndpointListener listener = new EndpointListener() {
        @Override
        public void update (EquipmentState state) {
            log.info("update State: {}", state);
        }

        @Override
        public void onNewTagValue (ISourceDataTag dataTag, ValueUpdate valueUpdate, SourceDataTagQuality quality) {
            log.info("onNewTagValue: dataTag {}, valueUpdate {}, quality {}", dataTag, valueUpdate, quality);
        }

        @Override
        public void onTagInvalid (ISourceDataTag dataTag, SourceDataTagQuality quality) {
            log.info("onNewTagValue: dataTag {}, quality {}", dataTag, quality);
        }

        @Override
        public void initialize (IEquipmentMessageSender sender) {}
    };


    @Test
    @UseConf("simengine_two_tag.xml")
    public void subscribeTagShouldReturnStateOK() throws EqIOException, InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Object> future = new CompletableFuture<>();

        handler.setEndpointListener(listener);
        handler.connectToDataSource();
//        future.get(10, TimeUnit.SECONDS);
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
