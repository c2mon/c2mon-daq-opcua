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
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.test.UseConf;
import cern.c2mon.daq.test.UseHandler;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@UseHandler(OPCUAMessageHandler.class)
public class OPCUAMessageHandlerAliveTest extends OPCUAMessageHandlerTestBase {

    TestListeners.TestListener sender;
    TestUtils.CommfaultSenderCapture capture;

    @Override
    protected void beforeTest() throws Exception {
        sender = new TestListeners.TestListener();
        super.beforeTest(sender);
        testEndpoint.setDelay(1);
        expect(testEndpoint.getMonitoredItem().getStatusCode()).andReturn(StatusCode.GOOD).anyTimes();
        replay(testEndpoint.getMonitoredItem());
        capture = new TestUtils.CommfaultSenderCapture(this.messageSender);
    }

    @Override
    protected void afterTest() throws Exception {
    }

    @Test
    @UseConf("alive_ok.xml")
    public void presentAliveTagShouldSendAliveOK() throws InterruptedException {
        configureContext(sender);
        replay(messageSender);
        try {
            handler.connectToDataSource();
        } catch (Exception e) {
            //Ignore errors, we only care about the message
            log.error("Exception: ", e);
        }
        assertTrue(sender.getAliveLatch().await(100L, TimeUnit.MILLISECONDS));
    }

    @Test
    @UseConf("alive_subeq.xml")
    public void multipleAliveTagsShouldSendAliveOK() throws InterruptedException {
        configureContext(sender);
        replay(messageSender);
        sender.setAliveLatch(new CountDownLatch(3));
        try {
            handler.connectToDataSource();
        } catch (Exception e) {
            //Ignore errors, we only care about the message
            log.error("Exception: ", e);
        }
        assertTrue(sender.getAliveLatch().await(20L, TimeUnit.MILLISECONDS));
    }

    @Test
    @UseConf("alive_subeq_false.xml")
    public void oneGoodOneBadAliveTagShouldSendAliveOKOncePerInterval() throws InterruptedException {
        configureContext(sender);
        replay(messageSender);
        sender.setAliveLatch(new CountDownLatch(2));
        try {
            handler.connectToDataSource();
        } catch (Exception e) {
            //Ignore errors, we only care about the message
            log.error("Exception: ", e);
        }
        assertFalse(sender.getAliveLatch().await(20L, TimeUnit.MILLISECONDS));
        assertEquals(1, sender.getAliveLatch().getCount());
    }
}

