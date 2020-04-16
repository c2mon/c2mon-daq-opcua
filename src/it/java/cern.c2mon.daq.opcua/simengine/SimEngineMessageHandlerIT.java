package cern.c2mon.daq.opcua.simengine;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.common.messaging.IProcessMessageSender;
import cern.c2mon.daq.opcua.ConnectionResolver;
import cern.c2mon.daq.opcua.OPCUAMessageHandler;
import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.connection.MiloClientWrapper;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.security.SecurityProvider;
import cern.c2mon.daq.opcua.upstream.EndpointListener;
import cern.c2mon.daq.test.GenericMessageHandlerTest;
import cern.c2mon.daq.test.UseConf;
import cern.c2mon.daq.test.UseHandler;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.easymock.Capture;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@UseHandler(OPCUAMessageHandler.class)
public class SimEngineMessageHandlerIT extends GenericMessageHandlerTest {

    private static ConnectionResolver resolver;
    OPCUAMessageHandler handler;
    SourceCommandTagValue value;
    CommfaultSenderCapture capture;

    private static long DATAID_VMON = 1L;
    private static long DATAID_PW = 2L;
    private static long CMDID_PW = 10L;
    private static long CMDID_V0SET = 20L;


    SecurityProvider p = new SecurityProvider();
    static AppConfig config;

    @BeforeClass
    public static void startServer() {

        config = AppConfig.builder()
                .appName("c2mon-opcua-daq")
                .applicationUri("urn:localhost:UA:C2MON")
                .productUri("urn:cern:ch:UA:C2MON")
                .organization("CERN")
                .organizationalUnit("C2MON team")
                .localityName("Geneva")
                .stateName("Geneva")
                .countryCode("CH")
                .build();

        // TODO: don't extend MessageHandler but use spring boot for DI, migrate all tests to junit 5
        GenericContainer image = new GenericContainer("gitlab-registry.cern.ch/mludwig/venuscaensimulationengine:venuscombo1.0.3")
                .waitingFor(Wait.forLogMessage(".*Server opened endpoints for following URLs:.*", 2))
                .withEnv("SIMCONFIG", "sim_BASIC.short.xml")
                .withNetworkMode("host");

        resolver = new ConnectionResolver(image);
        resolver.initialize();
    }

    @AfterClass
    public static void stopServer() {
        resolver.close();
    }

    @Override
    protected void beforeTest () throws Exception {
        handler = (OPCUAMessageHandler) msgHandler;
        capture = new CommfaultSenderCapture(messageSender);

        value = new SourceCommandTagValue();
        value.setDataType("java.lang.Integer");

        MiloClientWrapper wrapper = handler.getController().getEndpoint().getWrapper();
        wrapper.setConfig(config);
        p.setConfig(config);
        wrapper.setProvider(p);
    }

    @After
    public void cleanUp() throws Exception {
        //must be called before the handler disconnect in GenericMessageHandlerTest's afterTest method

        value.setValue(0);
        for(long id : handler.getEquipmentConfiguration().getSourceCommandTags().keySet()) {
            value.setId(id);
            handler.runCommand(value);
        }

        super.cleanUp();
    }
    @Override
    protected void afterTest () throws Exception {
    }

    @Test
    @UseConf("empty_datatags.xml")
    public void emptyDatatagsShouldThrowError () {
        assertThrows(ConfigurationException.class, () -> handler.connectToDataSource());
    }

    @Test
    @UseConf("simengine_power.xml")
    public void write1SetsValueTo1() throws EqIOException, EqCommandTagException, InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<SourceDataTagQuality> f = new CompletableFuture<>();
        handler.setEndpointListener(new EndpointTestListener(1, f, DATAID_PW));

        handler.connectToDataSource();
        setIDTo(CMDID_PW, 1);

        // Assert power is set to value
        SourceDataTagQuality quality = f.get(6000, TimeUnit.MILLISECONDS);
        assertEquals(SourceDataTagQualityCode.OK, quality.getQualityCode());

    }


    @Test
    @UseConf("simengine_power.xml")
    public void testPowerOnAndSetV0ShouldNotifyVMon() throws EqIOException, EqCommandTagException, InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<SourceDataTagQuality> f = new CompletableFuture<>();
        handler.setEndpointListener(new EndpointTestListener(10, f, DATAID_VMON));

        handler.connectToDataSource();
        setIDTo(CMDID_PW, 1);
        setIDTo(CMDID_V0SET, 10);

        // Assert monitored voltage close to threshold within reasonable time
        SourceDataTagQuality quality = f.get(6000, TimeUnit.MILLISECONDS);
        assertEquals(SourceDataTagQualityCode.OK, quality.getQualityCode());
    }

    private void setIDTo(long id, float val) throws EqCommandTagException {
        value.setValue(val);
        value.setId(id);
        handler.runCommand(value);
    }

    private static float valueUpdateToFloat(ValueUpdate valueUpdate) {
        Object t = ((DataValue) valueUpdate.getValue()).getValue().getValue();
        return (t instanceof Float) ? (float) t : (int) t;
    }


    @AllArgsConstructor
    private static class EndpointTestListener implements EndpointListener {
        @Getter
        private int threshold;
        CompletableFuture<SourceDataTagQuality> f;
        private long sourceID;

        boolean thresholdReached(ISourceDataTag dataTag, ValueUpdate valueUpdate) {
            return Math.abs(valueUpdateToFloat(valueUpdate) - threshold) < 0.5;
        }

        @Override
        public void update (EquipmentState state) {
            log.info("update State: {}", state);
        }

        @Override
        public void onNewTagValue (ISourceDataTag dataTag, ValueUpdate valueUpdate, SourceDataTagQuality quality) {
            log.info("onNewTagValue: dataTag {}, valueUpdate {}, quality {}", dataTag.getName(), valueUpdate.getValue(), quality);
            if (dataTag.getId().equals(sourceID) && thresholdReached(dataTag, valueUpdate)) {
                f.complete(quality);
            }
        }

        @Override
        public void onTagInvalid (ISourceDataTag dataTag, SourceDataTagQuality quality) {
            log.info("onNewTagValue: dataTag {}, quality {}", dataTag, quality);
        }

        @Override
        public void onWriteResponse(StatusCode statusCode, ISourceCommandTag tag) {

        }

        @Override
        public void initialize (IEquipmentMessageSender sender) {}
    }


    private static class CommfaultSenderCapture {
        Capture<Long> id = newCapture();
        Capture<String> tagName = newCapture();
        Capture<Boolean> val = newCapture();
        Capture<String> msg= newCapture();

        public CommfaultSenderCapture(IProcessMessageSender sender) {
            sender.sendCommfaultTag(captureLong(id), capture(tagName), captureBoolean(val), capture(msg));
            expectLastCall().once();
            replay(sender);
        }

        public void verifyCapture(Long id, String tagName, String msg) {
            assertEquals(id, this.id.getValue().longValue());
            assertEquals(tagName, this.tagName.getValue());
            assertEquals(msg, this.msg.getValue());
        }
    }
}
