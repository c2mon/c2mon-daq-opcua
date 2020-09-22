/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2020 CERN
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
package cern.c2mon.daq.opcua.simengine;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.OPCUAMessageHandler;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.taghandling.DataTagHandler;
import cern.c2mon.daq.opcua.taghandling.IDataTagHandler;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.test.GenericMessageHandlerTest;
import cern.c2mon.daq.test.UseConf;
import cern.c2mon.daq.test.UseHandler;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.common.process.EquipmentConfiguration;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import cern.c2mon.shared.daq.config.ChangeReport;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@UseHandler(OPCUAMessageHandler.class)
@SpringBootTest
@RunWith(SpringRunner.class)
@TestPropertySource(locations = "classpath:application.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SimEngineMessageHandlerIT extends GenericMessageHandlerTest {

    private static final int PILOT = 8890;
    private static final int SIMENGINE = 4841;
    private static final String environment = "sim_BASIC.short.xml";
    private static final long DATAID_VMON = 1L;
    private static final long DATAID_PW = 2L;
    private static final long CMDID_PW = 10L;
    private static final long CMDID_V0SET = 20L;

    @Autowired ApplicationContext context;

    private OPCUAMessageHandler handler;
    private IDataTagHandler tagHandler;
    private TestListeners.Pulse endpointListener;
    private SourceCommandTagValue value;

    @ClassRule
    public static final GenericContainer image = new GenericContainer("gitlab-registry.cern.ch/mludwig/venuscaensimulationengine:venuscombo1.0.3")
            .waitingFor(Wait.forLogMessage(".*Server opened endpoints for following URLs:.*", 2))
            .withEnv("SIMCONFIG", environment)
            .withExposedPorts(PILOT, SIMENGINE);

    @Override
    protected void beforeTest() throws Exception {
        handler = (OPCUAMessageHandler) msgHandler;
        handler.setContext(context);
        endpointListener = new TestListeners.Pulse();
        value = new SourceCommandTagValue();
        value.setDataType("java.lang.Integer");
        TestUtils.mapEquipmentAddress(equipmentConfiguration, image);
        log.info("############### TEST ##############");
    }

    @Override
    protected void afterTest() throws Exception {
    }

    @After
    public void cleanUp() throws Exception {
        log.info("############### CLEAN UP ##############");
        // must be called before the handler disconnect in GenericMessageHandlerTest's afterTest method
        log.info("Cleaning up... ");
        value.setValue(0);
        for (long id : handler.getEquipmentConfiguration().getSourceCommandTags().keySet()) {
            value.setId(id);
            handler.runCommand(value);
        }
        super.cleanUp();
    }

    @Test
    @UseConf("simengine_power.xml")
    public void refreshShouldTriggerValueUpdateForEachSubscribedTag() throws EqIOException {
        handler.connectToDataSource();
        MessageSender senderMock = niceMock(MessageSender.class);
        tagHandler = context.getBean(DataTagHandler.class);
        ReflectionTestUtils.setField(tagHandler, "messageSender", senderMock);

        final int tagNr = handler.getEquipmentConfiguration().getSourceDataTags().size();
        senderMock.onValueUpdate(anyLong(), anyObject(), anyObject());
        expectLastCall().times(tagNr);

        handler.refreshAllDataTags();
    }

    @Test
    @UseConf("simengine_power.xml")
    public void restartDAQShouldNotHaveIssuesWithEquipmentScope() throws EqIOException {
        handler.connectToDataSource();
        EquipmentConfiguration oldConfig = (EquipmentConfiguration) handler.getEquipmentConfiguration();
        EquipmentConfiguration config = oldConfig.clone();
        config.setEquipmentAddress("http://localhost:8890");
        final ChangeReport changeReport = new ChangeReport();

        handler.onUpdateEquipmentConfiguration(config, oldConfig, changeReport);
        assertTrue(changeReport.isSuccess());
        assertTrue(changeReport.getInfoMessage().contains("DAQ restarted."));
    }

    @Test
    @UseConf("simengine_power.xml")
    public void write1SetsValueTo1() throws EqIOException, EqCommandTagException, InterruptedException, ExecutionException, TimeoutException {
        endpointListener.setSourceID(DATAID_PW);
        endpointListener.setThreshold(1);
        connectAndInjectListener();

        setIDTo(CMDID_PW, 1);

        // Assert power is set to 1
        final ValueUpdate valueUpdate = endpointListener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS);
        assertEquals(1, valueUpdate.getValue());
    }


    @Test
    @UseConf("simengine_power.xml")
    public void testPowerOnAndSetV0ShouldNotifyVMon() throws EqIOException, EqCommandTagException {
        endpointListener.setSourceID(DATAID_VMON);
        endpointListener.setThreshold(10);
        connectAndInjectListener();
        setIDTo(CMDID_PW, 1);
        setIDTo(CMDID_V0SET, 10);

        // Assert monitored voltage close to threshold within reasonable time
        assertDoesNotThrow(() -> endpointListener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }


    @Test
    @UseConf("simengine_power_pulse.xml")
    public void testPowerOnAndOff() throws EqIOException, ExecutionException, InterruptedException, EqCommandTagException, TimeoutException {
        endpointListener.setSourceID(DATAID_PW);
        endpointListener.setThreshold(1);
        connectAndInjectListener();
        setIDTo(CMDID_PW, 1);

        final ValueUpdate pwOnUpdate = endpointListener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS);
        final ValueUpdate pwOffUpdate = endpointListener.getPulseTagUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS);
        final long timeDiff = pwOffUpdate.getSourceTimestamp() - pwOnUpdate.getSourceTimestamp();

        assertEquals(1, pwOnUpdate.getValue());
        assertEquals(0, pwOffUpdate.getValue());

        // A pulse length of 1 second, plus a margin for connection lag
        assertTrue(timeDiff >= 0 && timeDiff < 4000);
    }

    private void connectAndInjectListener() throws EqIOException {
        handler.connectToDataSource();
        tagHandler = context.getBean(DataTagHandler.class);
        Controller proxy = context.getBean(Controller.class);
        ReflectionTestUtils.setField(ReflectionTestUtils.getField(proxy, "endpoint"), "messageSender", endpointListener);

    }

    private void setIDTo(long id, float val) throws EqCommandTagException {
        value.setValue(val);
        value.setId(id);
        handler.runCommand(value);
    }
}
