package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionReader;
import cern.c2mon.daq.opcua.testutils.ExceptionTestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AliveWriterTest {
    TestListeners.TestListener listener = new TestListeners.TestListener();
    TagSubscriptionReader mapper = new TagSubscriptionMapper();
    AliveWriter aliveWriter = new AliveWriter(TestUtils.getFailoverProxy(new TestEndpoint(listener, mapper), listener), listener);

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(aliveWriter, "writeTime", 1L);
        final OPCHardwareAddressImpl hwAddress = new OPCHardwareAddressImpl("Test");
        hwAddress.setCurrentOPCItemName("test");
        DataTagAddress tagAddress = new DataTagAddress(hwAddress, 0, (short) 0, 0, 0, 2, true);
        final SourceDataTag iSourceDataTag = new SourceDataTag((long) 1, "test", false, (short) 0, null, tagAddress);
        ReflectionTestUtils.setField(aliveWriter, "address", ItemDefinition.of(iSourceDataTag).getNodeId());
    }

    @AfterEach
    public void tearDown() {
        aliveWriter.stopWriter();
    }

    @Test
    public void writeAliveShouldNotifyListenerWithGoodStatusCode() {
        ReflectionTestUtils.setField(aliveWriter, "controllerProxy", TestUtils.getFailoverProxy(new TestEndpoint(listener, mapper), listener));
        aliveWriter.startWriter();
        assertDoesNotThrow(() -> listener.getAlive().get(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void exceptionInWriteAliveShouldNotNotifyListener() {
        ReflectionTestUtils.setField(aliveWriter, "controllerProxy", TestUtils.getFailoverProxy(new ExceptionTestEndpoint(listener, mapper), listener));
        aliveWriter.startWriter();
        assertThrows(TimeoutException.class, () -> listener.getAlive().get(100, TimeUnit.MILLISECONDS));
    }
}
