/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2022 CERN
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
package cern.c2mon.daq.opcua.opcuamessagehandler;

import cern.c2mon.daq.opcua.OPCUAMessageHandler;
import cern.c2mon.daq.opcua.OPCUAMessageSender;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.metrics.MetricProxy;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.test.UseConf;
import cern.c2mon.daq.test.UseHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static cern.c2mon.daq.opcua.MessageSender.EquipmentState.CONNECTION_FAILED;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@UseHandler(OPCUAMessageHandler.class)
public class OPCUAMessageHandlerCommfaultTest extends OPCUAMessageHandlerTestBase {

    OPCUAMessageSender sender = new OPCUAMessageSender(new MetricProxy(new SimpleMeterRegistry()));
    TestUtils.CommfaultSenderCapture capture;

    @Override
    protected void beforeTest() throws Exception {
        super.beforeTest(sender);
        testEndpoint.setThrowExceptions(true);
        testEndpoint.setDelay(100);
        capture = new TestUtils.CommfaultSenderCapture(this.messageSender);
    }

    @Override
    protected void afterTest() throws Exception {
    }

    @Test
    @UseConf("commfault_ok.xml")
    public void properConfigButBadEndpointShouldThrowCommunicationError() {
        configureContext(sender);
        replay(messageSender);
        assertThrows(CommunicationException.class, handler::connectToDataSource);
    }

    @Test
    @UseConf("commfault_ok.xml")
    public void properConfigButBadEndpointShouldSendFAIL() {
        configureContext(sender);
        replay(messageSender);
        try {
            handler.connectToDataSource();
        } catch (Exception e) {
            //Ignore errors, we only care about the message
            log.error("Exception: ", e);
        }
        capture.verifyCapture(107211L,
                handler.getEquipmentConfiguration().getName() + ":COMM_FAULT",
                CONNECTION_FAILED.message);
    }

    @Test
    @UseConf("bad_address_string.xml")
    public void badAddressStringShouldThrowError() {
        configureContext(sender);
        replay(messageSender);
        assertThrows(ConfigurationException.class,
                () -> handler.connectToDataSource(),
                ExceptionContext.URI_SYNTAX.getMessage());
    }

    @Test
    @UseConf("commfault_ok.xml")
    public void cannotEstablishInitialConnectionShouldSendFAIL() {
        configureContext(sender);
        replay(messageSender);
        try {
            handler.connectToDataSource();
        } catch (Exception e) {
            //Ignore errors, we only care about the message
            log.error("Exception: ", e);
        }
        capture.verifyCapture(107211L,
                handler.getEquipmentConfiguration().getName() + ":COMM_FAULT",
                CONNECTION_FAILED.message);
    }
}

