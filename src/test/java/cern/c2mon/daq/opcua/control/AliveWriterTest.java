package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.testutils.ExceptionTestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.shared.common.datatag.address.impl.OPCHardwareAddressImpl;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class AliveWriterTest {
    TestListeners.TestListener listener = new TestListeners.TestListener();
    AliveWriter aliveWriter = new AliveWriter(new TestEndpoint(), listener);

    @BeforeEach
    public void setUp() {
        aliveWriter.stopWriter();
        ReflectionTestUtils.setField(aliveWriter, "writeTime", 1L);
        ReflectionTestUtils.setField(aliveWriter, "address", new OPCHardwareAddressImpl("Test"));
    }

    @Test
    public void writeAliveShouldNotifyListenerWithGoodStatusCode() throws ExecutionException, InterruptedException, TimeoutException, CommunicationException, ConfigurationException {
        aliveWriter.startWriter();
        Assertions.assertEquals(StatusCode.GOOD, listener.getAlive().get(3000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void exceptionInWriteAliveShouldNotNotifyListener() {
        ReflectionTestUtils.setField(aliveWriter, "endpoint", new ExceptionTestEndpoint());
        aliveWriter.startWriter();
        assertThrows(TimeoutException.class, () -> listener.getAlive().get(3000, TimeUnit.MILLISECONDS));
    }
}
