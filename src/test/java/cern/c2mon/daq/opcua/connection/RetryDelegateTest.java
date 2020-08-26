package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.exceptions.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.WRITE;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class RetryDelegateTest {

    AppConfigProperties config;
    RetryDelegate delegate;

    @BeforeEach
    public void setUp() {
        config = AppConfigProperties.builder().maxRetryAttempts(3).requestTimeout(300).retryDelay(1000).build();
        delegate = new RetryDelegate(config);
    }

    private StatusCode runWith(long disconnectedOn, Exception e) throws OPCUAException {
        CompletableFuture<StatusCode> f = new CompletableFuture<>();
        f.completeExceptionally(e);
        return delegate.completeOrRetry(WRITE, () -> disconnectedOn, () -> f);
    }

    @Test
    public void alreadyAttemptingResubscriptionTooLongThrowsLongLostConnectionException() {
        final LongLostConnectionException e = new LongLostConnectionException(ExceptionContext.WRITE, new IllegalArgumentException());
        assertThrows(CommunicationException.class, () -> runWith(20L, e));
    }

    @Test
    public void interruptingThreadShouldBeCauseOfOPCUAException() throws InterruptedException {
        final CompletableFuture<StatusCode> f = new CompletableFuture<>();
        f.thenApply(s -> {
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                log.info("Interrupted");
            }
            return StatusCode.GOOD;
        });

        final ExecutorService e = Executors.newFixedThreadPool(1);
        e.submit(() -> {
            try {
                delegate.completeOrRetry(WRITE, () -> 20L, () -> f);
            } catch (OPCUAException ex) {
                assertTrue(ex.getCause() instanceof InterruptedException);
            }
        });
        e.shutdownNow();
        e.awaitTermination(6, TimeUnit.SECONDS);
    }

    @Test
    public void endpointStoppedShouldThrowEndpointDisconnectedException() {
        final CompletableFuture<StatusCode> f = new CompletableFuture<>();
        f.complete(StatusCode.GOOD);
        assertThrows(EndpointDisconnectedException.class,
                () -> delegate.completeOrRetry(WRITE, () -> -1L, () -> f));
    }

    @Test
    public void anyOtherExceptionShouldThrowCommunicationException() {
        assertThrows(CommunicationException.class, () -> runWith(0L, new IllegalArgumentException()));
    }

    @Test
    public void unexpectedExceptionShouldThrowCommunicationException() {
        assertThrows(CommunicationException.class, () -> delegate.completeOrRetry(WRITE, () -> 0L, null));
    }
    @Test
    public void successfulExecutionShouldReturnStatusCode() throws OPCUAException {
        final StatusCode expected = StatusCode.GOOD;
        final CompletableFuture<StatusCode> f = new CompletableFuture<>();
        f.complete(expected);
        assertEquals(expected, delegate.completeOrRetry(WRITE, () -> 0L, () -> f));
    }
}
