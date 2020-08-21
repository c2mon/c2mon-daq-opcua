package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionReader;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.HardwareAddress;
import cern.c2mon.shared.common.datatag.address.impl.DIPHardwareAddressImpl;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AliveWriterTest {
    TestListeners.TestListener listener = new TestListeners.TestListener();
    TagSubscriptionReader mapper = new TagSubscriptionMapper();
    AliveWriter aliveWriter;
    SourceDataTag aliveTag;
    TestEndpoint testEndpoint;

    @BeforeEach
    public void setUp() {
        final OPCHardwareAddressImpl hwAddress = new OPCHardwareAddressImpl("Test");
        hwAddress.setCurrentOPCItemName("test");
        DataTagAddress tagAddress = new DataTagAddress(hwAddress, 0, (short) 0, 0, 0, 2, true);
        aliveTag = new SourceDataTag((long) 1, "test", false, (short) 0, null, tagAddress);
        testEndpoint = new TestEndpoint(listener, mapper);
        aliveWriter =  new AliveWriter(TestUtils.getFailoverProxy(testEndpoint, listener), listener);
    }

    @AfterEach
    public void tearDown() {
        aliveWriter.stopWriter();
    }

    @Test
    public void aliveWriterShouldNotStartOnInvalidAliveTagAddress() {
        final HardwareAddress dip = new DIPHardwareAddressImpl("bad address type");
        DataTagAddress tagAddress = new DataTagAddress(dip, 0, (short) 0, 0, 0, 2, true);
        aliveTag = new SourceDataTag((long) 1, "test", false, (short) 0, null, tagAddress);

        aliveWriter.startAliveWriter(aliveTag, 2L);
        assertThrows(TimeoutException.class, () -> listener.getAlive().get(0).get(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void writeAliveShouldNotifyListenerWithGoodStatusCode() {
        final CompletableFuture<Void> alive = listener.getAlive().get(0);
        aliveWriter.startAliveWriter(aliveTag, 2L);
        assertDoesNotThrow(() -> alive.get(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void exceptionInWriteAliveShouldNotNotifyListener() {
        testEndpoint.setThrowExceptions(true);
        aliveWriter.startAliveWriter(aliveTag, 2L);
        assertThrows(TimeoutException.class, () -> listener.getAlive().get(0).get(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void badStatusCodeShouldNotNotifyListener() {
        testEndpoint.setReturnGoodStatusCodes(false);
        aliveWriter.startAliveWriter(aliveTag, 2L);
        assertThrows(TimeoutException.class, () -> listener.getAlive().get(0).get(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void cancellingAliveWriterTwiceShouldDoNothing() {
        aliveWriter.startAliveWriter(aliveTag, 2L);
        aliveWriter.stopWriter();
        assertDoesNotThrow(() -> aliveWriter.stopWriter());
    }

    @Test
    public void stopUninitializedAliveWriterShouldDoNothing() {
        assertDoesNotThrow(() -> aliveWriter.stopWriter());
    }
}
