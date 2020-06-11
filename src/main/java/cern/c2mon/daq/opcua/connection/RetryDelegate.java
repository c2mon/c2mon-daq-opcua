package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.*;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The RetryDelegate executes a given method a number of times in case of failure until the method completes
 * successfully with a delay as given in the application configuration. It simultaneously keeps track of how long the
 * client has been disconnected from the server. If that period is longer than the total period likely needed to
 * complete all retries, a method is only executed once before it fails.
 * One RetryDelegate is created for each {@link Endpoint}.
 */
@Component(value = "retryDelegate")
@Scope(value = "prototype")
public class RetryDelegate implements SessionActivityListener {

    @Value("${app.serverTimeout}")
    private int timeout;

    @Value("${app.maxRetryAttempts}")
    private int maxRetryCount;

    @Value("${app.retryDelay}")
    private long retryDelay;

    private long disconnectionInstant = 0L;

    /**
     * Called when the client is connected to the server and the session activates.
     * @param session the current session
     */
    @Override
    public void onSessionActive(UaSession session) {
        this.disconnectionInstant = 0L;
    }

    /**
     * Called when the client is disconnected from the server and the session deactivates.
     * @param session the current session
     */
    @Override
    public void onSessionInactive(UaSession session) {
        this.disconnectionInstant = System.currentTimeMillis();
    }

    /**
     * Attempt to execute the get() method of a given supplier for a certain number of times with a timeout given as
     * app.serverTimeout in the application configuration. If the action fails due to a reason indicating a
     * configuration issue, a {@link ConfigurationException} is thrown and no further attempts are made. Similarly, if
     * the client has been disconnected from the server for a period of time longer than the estimated time spent
     * executing all retry attempts, no further attempts are made and a {@link LongLostConnectionException} is thrown.
     * In all other cases, the supplier.get() method is attempted for a maximum amount of times given in the application
     * configuration as app.maxRetryAttempts with a delay in between executions of app.retryDelay.
     * @param context        the context of the supplier.
     * @param futureSupplier a supplier returning the CompletableFuture with the action to be executed.
     * @param <T>            The return type of the CompletableFuture returned by the supplier.
     * @return the result of the operation represented as a CompletableFuture returned by futureSupplier
     * @throws OPCUAException of type {@link ConfigurationException} if the supplier fails due to a reason indicating a
     *                        configuration issue, or type {@link LongLostConnectionException} is the time of
     *                        disconnection from the server is greater than the time needed to attempt all retries, and
     *                        of type {@link CommunicationException} is all retries attempts have been exhausted without
     *                        a success.
     */

    @Retryable(value = {CommunicationException.class},
            maxAttemptsExpression = "${app.maxRetryAttempts}",
            backoff = @Backoff(delayExpression = "${app.retryDelay}"))
    public <T> T completeOrThrow(ExceptionContext context, Supplier<CompletableFuture<T>> futureSupplier) throws OPCUAException {
        try {
            // The call must be passed as a supplier rather than a future. Otherwise only the join() method is repeated,
            // but not the underlying action on the OpcUaClient
            return futureSupplier.get().orTimeout(timeout, TimeUnit.MILLISECONDS).join();
        } catch (CompletionException e) {
            boolean longDisconnection = (disconnectionInstant > 0)
                    && maxRetryCount * (retryDelay + timeout) < System.currentTimeMillis() - disconnectionInstant;
            throw OPCUAException.of(context, e.getCause(), longDisconnection);
        }
    }
}