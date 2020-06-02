package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static cern.c2mon.daq.opcua.exceptions.OPCUAException.of;

/**
 * The RetryDelegate executes a given method a number of times in case of failure until the method completes
 * successfully with a delay as given in the application configuration. It simultaneously keeps track of how long the
 * client has been disconnected from the server. If that period is longer than the total period likely needed to
 * complete all retries, a method is only executed once before it fails.
 */
@Component(value = "retryDelegate")
public class RetryDelegate {

    @Value("${app.serverTimeout}")
    private int timeout;

    @Value("${app.maxRetryAttempts}")
    private int maxRetryCount;

    @Value("${app.retryDelay}")
    private int retryDelay;

    private long disconnectionInstant = 0L;

    /**
     * Called by the {@link EndpointListener} upon changes in the state of the session.
     * @param connected a flag indicating the state of the session as active or inactive
     */
    public void setConnectionState(boolean connected) {
        this.disconnectionInstant = connected ? 0L : System.currentTimeMillis();
    }

    /**
     * Attempt to execute the get() method of a given supplier for a certain number of times with a timeout given as
     * app.serverTimeout in the application configuration. If the supplier fails due to a reason indicating a
     * configuration issue, a {@link ConfigurationException} is thrown and no further attempts are made. Similarly, if
     * the client has been disconnected from the server for a period of time longer than the estimated time spent
     * executing all retry attempts, no further attempts are made and a {@link LongLostConnectionException} is thrown.
     * In all other cases, the supplier.get() method is attempted for a maximum amount of times given in the application
     * configuration as app.maxRetryAttempts with a delay in between executions of app.retryDelay.
     * @param context  the context of the supplier.
     * @param supplier the supplier to be executed.
     * @param <T>      The return type of the supplier.
     * @return the return value of the generic supplier.
     * @throws OPCUAException       of type {@link ConfigurationException} if the supplier fails due to a reason
     *                              indicating a configuration issue, or type {@link LongLostConnectionException} is the
     *                              time of disconnection from the server is greater than the time needed to attempt all
     *                              retries, and of type {@link CommunicationException} is all retries attempts have
     *                              been exhaused without a success.
     * @throws InterruptedException if the thread was interrupted before the supplier could be executed successfully. No
     *                              retries are attempted after this exception.
     */
    @Retryable(value = {CommunicationException.class},
            maxAttemptsExpression = "${app.maxRetryAttempts}",
            backoff = @Backoff(delayExpression = "${app.retryDelay}"))
    public <T> T completeOrThrow(ExceptionContext context, Supplier<T> supplier) throws OPCUAException, InterruptedException {
        try {
            return CompletableFuture.supplyAsync(supplier).get(timeout, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            final Throwable t = (e instanceof ExecutionException) ? e.getCause() : e;
            throw of(context, t, isDisconnectionPeriodTooLong());
        }
    }

    private boolean isDisconnectionPeriodTooLong() {
        if (disconnectionInstant > 0) {
            return maxRetryCount * (retryDelay + timeout) < System.currentTimeMillis() - disconnectionInstant;
        }
        return false;
    }
}