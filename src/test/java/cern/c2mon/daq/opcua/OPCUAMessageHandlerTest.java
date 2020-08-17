package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionManager;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.taghandling.*;
import cern.c2mon.daq.opcua.testutils.MiloMocker;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.test.GenericMessageHandlerTest;
import cern.c2mon.daq.test.UseConf;
import cern.c2mon.daq.test.UseHandler;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.EquipmentConfiguration;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import cern.c2mon.shared.daq.config.ChangeReport;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

@UseHandler(OPCUAMessageHandler.class)
public class OPCUAMessageHandlerTest extends GenericMessageHandlerTest {

    ApplicationContext context = createMock(ApplicationContext.class);
    OPCUAMessageHandler handler;
    MiloMocker mocker;
    MessageSender sender = niceMock(MessageSender.class);
    TagSubscriptionManager mapper = new TagSubscriptionMapper();
    TestEndpoint endpoint = new TestEndpoint(sender, mapper);
    AppConfigProperties appConfigProperties = TestUtils.createDefaultConfig();
    Controller controller = TestUtils.getFailoverProxy(endpoint, sender);
    IDataTagHandler dataTagHandler = new DataTagHandler(mapper, sender, controller);
    CommandTagHandler commandTagHandler = new CommandTagHandler(controller);
    DataTagChanger dataTagChanger = new DataTagChanger(dataTagHandler);
    AliveWriter writer = new AliveWriter(controller, sender);

    @Override
    protected void beforeTest() throws Exception {
        appConfigProperties.setRetryDelay(0);
        handler = (OPCUAMessageHandler) msgHandler;
        handler.setContext(context);

        mocker = new MiloMocker(endpoint, mapper);
        configureContextWithController(controller);

        final Collection<ISourceDataTag> tags = handler.getEquipmentConfiguration().getSourceDataTags().values();
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tags);
        replay(context, endpoint.getMonitoredItem());
        handler.connectToDataSource();
    }

    @Override
    protected void afterTest() throws Exception {

    }

    private void configureContextWithController(Controller controller) {
        expect(context.getBean("controller", Controller.class)).andReturn(controller).anyTimes();
        expect(context.getBean(IDataTagHandler.class)).andReturn(dataTagHandler).anyTimes();
        expect(context.getBean(CommandTagHandler.class)).andReturn(commandTagHandler).anyTimes();
        expect(context.getBean(AppConfigProperties.class)).andReturn(appConfigProperties).anyTimes();
        expect(context.getBean(DataTagChanger.class)).andReturn(dataTagChanger).anyTimes();
        expect(context.getBean(AliveWriter.class)).andReturn(writer).anyTimes();
        expect(context.getBean(MessageSender.class)).andReturn(sender).anyTimes();
    }

    @Test
    @UseConf("mock_test.xml")
    public void refreshShouldTriggerValueUpdateForEachSubscribedTag() {
        resetToNice(sender);
        sender.onValueUpdate(anyLong(), anyObject(), anyObject());
        expectLastCall().times(handler.getEquipmentConfiguration().getSourceDataTags().values().size());
        replay(sender);

        handler.refreshAllDataTags();
        verify(sender);
    }

    @Test
    @UseConf("mock_test.xml")
    public void refreshTagShouldTriggerValueUpdate() {
        resetToNice(sender);
        Capture<Long> idCapture = newCapture(CaptureType.ALL);
        sender.onValueUpdate(captureLong(idCapture), anyObject(), anyObject());
        expectLastCall().once();
        replay(sender);

        handler.refreshDataTag(1L);
        assertEquals(1L, idCapture.getValue());

    }

    @Test
    @UseConf("mock_test.xml")
    public void refreshUnknownTagShouldDoNothing() {
        resetToStrict(sender);
        replay(sender);

        handler.refreshDataTag(-1L);
        verify(sender);
    }

    @Test
    @UseConf("mock_test.xml")
    public void equipmentConfigurationUpdateToNewAddressShouldRestartDAQ() {
        EquipmentConfiguration oldConfig = (EquipmentConfiguration) handler.getEquipmentConfiguration();
        EquipmentConfiguration config = oldConfig.clone();
        config.setEquipmentAddress("http://test:2");
        final ChangeReport changeReport = new ChangeReport();

        handler.onUpdateEquipmentConfiguration(config, oldConfig, changeReport);
        assertTrue(changeReport.isSuccess());
        assertTrue(changeReport.getInfoMessage().contains("DAQ restarted."));
    }

    @Test
    @UseConf("mock_test.xml")
    public void equipmentConfigurationShouldFailIfInterrupted() throws InterruptedException {
        appConfigProperties.setRetryDelay(5000L);
        EquipmentConfiguration oldConfig = (EquipmentConfiguration) handler.getEquipmentConfiguration();
        EquipmentConfiguration config = oldConfig.clone();
        config.setEquipmentAddress("http://test:2");
        final ChangeReport changeReport = new ChangeReport();
        ExecutorService s = Executors.newFixedThreadPool(2);

        s.submit(() -> handler.onUpdateEquipmentConfiguration(config, oldConfig, changeReport));
        TimeUnit.MILLISECONDS.sleep(100L);
        s.shutdownNow();
        s.awaitTermination(5000L, TimeUnit.MILLISECONDS);
        assertTrue(changeReport.isFail());
    }

    @Test
    @UseConf("mock_test.xml")
    public void equipmentConfigurationUpdateShouldFailOnException() {
        EquipmentConfiguration oldConfig = (EquipmentConfiguration) handler.getEquipmentConfiguration();
        EquipmentConfiguration config = oldConfig.clone();
        config.setEquipmentAddress("http://test:2");
        final ChangeReport changeReport = new ChangeReport();
        endpoint.setThrowExceptions(true);

        handler.onUpdateEquipmentConfiguration(config, oldConfig, changeReport);
        assertTrue(changeReport.isFail());
    }

    @Test
    @UseConf("mock_test.xml")
    public void equipmentConfigurationUpdateShouldApplyDataTagChanges() {
        final IEquipmentConfiguration oldConfig = handler.getEquipmentConfiguration();
        EquipmentConfiguration config = new EquipmentConfiguration();
        config.setEquipmentAddress(oldConfig.getAddress());
        handler.onUpdateEquipmentConfiguration(config, oldConfig, new ChangeReport());

        assertTrue(mapper.getTagIdDefinitionMap().isEmpty());
    }

    @Test
    @UseConf("mock_test.xml")
    public void equipmentConfigurationShouldRestartAliveWriter() {
        appConfigProperties.setAliveWriterEnabled(true);
        EquipmentConfiguration oldConfig = (EquipmentConfiguration) handler.getEquipmentConfiguration();
        EquipmentConfiguration config = oldConfig.clone();
        config.setAliveTagId(2);
        config.setAliveTagInterval(2000L);
        final ChangeReport changeReport = new ChangeReport();
        handler.onUpdateEquipmentConfiguration(config, oldConfig, changeReport);

        assertTrue(changeReport.getInfoMessage().contains("Alive Writer updated."));
    }

    @Test
    @UseConf("mock_test.xml")
    public void equipmentConfigurationShouldReportBadAliveTagInterval() {
        appConfigProperties.setAliveWriterEnabled(true);
        EquipmentConfiguration oldConfig = (EquipmentConfiguration) handler.getEquipmentConfiguration();
        EquipmentConfiguration config = oldConfig.clone();
        config.setAliveTagId(2);
        final ChangeReport changeReport = new ChangeReport();
        handler.onUpdateEquipmentConfiguration(config, oldConfig, changeReport);

        assertTrue(changeReport.getErrorMessage().contains(ExceptionContext.BAD_ALIVE_TAG_INTERVAL.getMessage()));
    }

    @Test
    @UseConf("mock_test.xml")
    public void equipmentConfigurationShouldReportUnknownAliveTag() {
        appConfigProperties.setAliveWriterEnabled(true);
        IEquipmentConfiguration oldConfig = handler.getEquipmentConfiguration();
        EquipmentConfiguration config = new EquipmentConfiguration();
        config.setEquipmentAddress(oldConfig.getAddress());
        config.setAliveTagId(2);
        config.setAliveTagInterval(2000L);
        final ChangeReport changeReport = new ChangeReport();
        handler.onUpdateEquipmentConfiguration(config, oldConfig, changeReport);

        assertTrue(changeReport.getErrorMessage().contains(ExceptionContext.BAD_ALIVE_TAG.getMessage()));
    }

    @Test
    @UseConf("mock_test.xml")
    public void equipmentUpdateShouldStartAliveWriterIfEnabled() {
        final TestListeners.TestListener testListener = new TestListeners.TestListener();
        ReflectionTestUtils.setField(writer, "messageSender", testListener);
        appConfigProperties.setAliveWriterEnabled(true);
        EquipmentConfiguration oldConfig = (EquipmentConfiguration) handler.getEquipmentConfiguration();
        EquipmentConfiguration config = oldConfig.clone();
        config.setAliveTagId(2);
        config.setAliveTagInterval(100L);
        final ChangeReport changeReport = new ChangeReport();
        final CompletableFuture<Void> f = testListener.getAlive().get(0);
        handler.onUpdateEquipmentConfiguration(config, oldConfig, changeReport);
        Assertions.assertDoesNotThrow(() -> f.get(5000L, TimeUnit.MILLISECONDS));
    }

    @Test
    @UseConf("mock_test.xml")
    public void equipmentUpdateShouldStartAliveWriterIfOnlyIntervalChanges() {
        final TestListeners.TestListener testListener = new TestListeners.TestListener();
        ReflectionTestUtils.setField(writer, "messageSender", testListener);
        appConfigProperties.setAliveWriterEnabled(true);
        EquipmentConfiguration oldConfig = (EquipmentConfiguration) handler.getEquipmentConfiguration();
        EquipmentConfiguration config = oldConfig.clone();
        oldConfig.setAliveTagId(2L);
        config.setAliveTagId(2L);
        oldConfig.setAliveTagInterval(100L);
        config.setAliveTagInterval(200L);
        final ChangeReport changeReport = new ChangeReport();
        final CompletableFuture<Void> f = testListener.getAlive().get(0);
        handler.onUpdateEquipmentConfiguration(config, oldConfig, changeReport);
        Assertions.assertDoesNotThrow(() -> f.get(5000L, TimeUnit.MILLISECONDS));
    }

    @Test
    @UseConf("mock_test.xml")
    public void equipmentRestartShouldStartAliveWriterIfEnabled() {
        final TestListeners.TestListener testListener = new TestListeners.TestListener();
        appConfigProperties.setAliveWriterEnabled(true);
        appConfigProperties.setRestartDelay(2);
        EquipmentConfiguration oldConfig = (EquipmentConfiguration) handler.getEquipmentConfiguration();
        oldConfig.setAliveTagInterval(100L);
        oldConfig.setAliveTagId(2);
        EquipmentConfiguration config = oldConfig.clone();
        config.setAddress("test2");
        final ChangeReport changeReport = new ChangeReport();
        final CompletableFuture<Void> f = testListener.getAlive().get(0);
        handler.onUpdateEquipmentConfiguration(config, oldConfig, changeReport);
        ReflectionTestUtils.setField(writer, "messageSender", testListener);
        Assertions.assertDoesNotThrow(() -> f.get(5000L, TimeUnit.MILLISECONDS));
    }

    @Test
    @UseConf("mock_test.xml")
    public void runUnknownCommandShouldThrowException() {
        final SourceCommandTagValue value = new SourceCommandTagValue();
        value.setId(-1L);
        assertThrows(EqCommandTagException.class, ()-> handler.runCommand(value));
    }

    @Test
    @UseConf("mock_test.xml")
    public void runKnownCommandShouldCallCommandTagHandlerWithTag() throws EqCommandTagException {
        final SourceCommandTagValue value = new SourceCommandTagValue();
        value.setId(20L);
        assertNull(handler.runCommand(value));
    }
}
