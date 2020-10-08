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
package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionReader;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import cern.c2mon.shared.common.datatag.util.JmsMessagePriority;
import cern.c2mon.shared.common.datatag.util.ValueDeadbandType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class AliveWriterTest {
    TestListeners.TestListener listener = new TestListeners.TestListener();
    TagSubscriptionReader mapper = new TagSubscriptionMapper();
    AliveWriter aliveWriter;
    SourceDataTag aliveTag;
    SourceDataTag subAliveTag;
    TestEndpoint testEndpoint;

    @BeforeEach
    public void setUp() {
        final OPCHardwareAddressImpl hwAddress = new OPCHardwareAddressImpl("Test");
        hwAddress.setCurrentOPCItemName("test");
        DataTagAddress tagAddress = new DataTagAddress(hwAddress, 0, ValueDeadbandType.NONE, 0, 0, JmsMessagePriority.PRIORITY_LOW, true);
        aliveTag = new SourceDataTag((long) 1, "test", false, (short) 0, null, tagAddress);
        subAliveTag = new SourceDataTag((long) 1, "test", false, (short) 0, null, tagAddress);
        testEndpoint = new TestEndpoint(listener, mapper);
        aliveWriter =  new AliveWriter(TestUtils.getFailoverProxy(testEndpoint, listener), listener);
    }

    @AfterEach
    public void tearDown() {
        aliveWriter.stopAliveWriter();
    }

    @Test
    public void writeAliveShouldNotifyListenerWithGoodStatusCode() throws InterruptedException {
        aliveWriter.startAliveWriter(aliveTag, 2L);
        assertTrue(listener.getAliveLatch().await(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void writeAliveShouldNotifyListenerWithGoodStatusCodeSeveralTimes() throws InterruptedException {
        listener.setAliveLatch(new CountDownLatch(2));
        aliveWriter.startAliveWriter(aliveTag, 2L);
        assertTrue(listener.getAliveLatch().await(4L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void writeAliveWithSubEquipmentsShouldNotifyListenerWithGoodStatusCode() throws InterruptedException {
        listener.setAliveLatch(new CountDownLatch(2));
        aliveWriter.startAliveWriter(aliveTag, 2L);
        aliveWriter.startAliveWriter(subAliveTag, 2L);
        assertTrue(listener.getAliveLatch().await(100, TimeUnit.MILLISECONDS));
    }
    @Test
    public void writeAliveWithSubEquipmentsShouldNotifyListenerWithGoodStatusCodeSeveralTimes() throws InterruptedException {
        listener.setAliveLatch(new CountDownLatch(6));
        aliveWriter.startAliveWriter(aliveTag, 2L);
        aliveWriter.startAliveWriter(subAliveTag, 2L);
        assertTrue(listener.getAliveLatch().await(100L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void exceptionInWriteAliveShouldNotNotifyListener() throws InterruptedException {
        testEndpoint.setThrowExceptions(true);
        aliveWriter.startAliveWriter(aliveTag, 2L);
        assertFalse(listener.getAliveLatch().await(50L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void badStatusCodeShouldNotNotifyListener() throws InterruptedException {
        testEndpoint.setReturnGoodStatusCodes(false);
        aliveWriter.startAliveWriter(aliveTag, 2L);
        assertFalse(listener.getAliveLatch().await(50L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void cancellingAliveWriterTwiceShouldDoNothing() {
        aliveWriter.startAliveWriter(aliveTag, 2L);
        aliveWriter.stopAliveWriter();
        assertDoesNotThrow(() -> aliveWriter.stopAliveWriter());
    }

    @Test
    public void stopUninitializedAliveWriterShouldDoNothing() {
        assertDoesNotThrow(() -> aliveWriter.stopAliveWriter());
    }
}
