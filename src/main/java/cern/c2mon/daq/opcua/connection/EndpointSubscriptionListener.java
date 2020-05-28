package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscriptionManager;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Component("endpointSubscriptionListener")
@Slf4j
public class EndpointSubscriptionListener implements UaSubscriptionManager.SubscriptionListener {

    private Controller controller;

    @Autowired
    public void setController(Controller controller) {
        this.controller = controller;
    }

    @Autowired
    private RetryTemplate subscriptionRetryTemplate;

    @Getter
    private AtomicInteger numRetry = new AtomicInteger(0);

    /**
     * If a Subscription is transferred to another Session, the queued Notification Messages for this subscription are
     * moved from the old to the new subscription.
     * @param subscription
     * @param statusCode
     */
    @Override
    public void onSubscriptionTransferFailed(UaSubscription subscription, StatusCode statusCode) {
        log.info("onSubscriptionTransferFailed event for {} : StatusCode {}", subscription.toString(), statusCode);
        recreate(subscription);
    }

    /**
     * Subscriptions have a keep-alive counter that counts the number of consecutive publishing cycles in which there
     * have been no Notifications to report to the Client. onKeepAlive is called when the maximum keep-alive count is
     * reached to inform the Client that the Subscription is still active. The maximum keep-alive count is set by the
     * Client during Subscription creation. TODO: should this be specified in the C2MON EquipmentAddress?
     * @param subscription The subscription whose keep-alive counter has hit the maximum keep-alive count since the last
     *                     Notification
     * @param publishTime
     */
    @Override
    public void onKeepAlive(UaSubscription subscription, DateTime publishTime) {
        log.info("onKeepAlive event for {}  at time {}", subscription.toString(), publishTime);

    }

    /**
     * The client must send PublishRequests to the server to enable to send PublishResponses back. These are used to
     * deliver the notification to the client. A Subscription closes automatically when there has been no processing of
     * a Publish request nor Service calls using the SubscriptionId of the Subscription within the number of consecutive
     * publishing cycles specified by the lifetime counter of the Subscription. In this case, onStatusChange is called
     * with the status code Bad_Timeout. The subscription's monitored items are deleted by the server when the
     * subscription times out. The other status changes (CLOSED, CREATING, NORMAL, LATE, KEEPALIVE) don't need to be
     * handled specifically.
     * @param subscription the subscription which times out
     * @param status       the new status of the subscription
     */
    @Override
    public void onStatusChanged(UaSubscription subscription, StatusCode status) {
        log.info("onStatusChanged event for {} : StatusCode {}", subscription.toString(), status);
        if (status.isBad() && status.getValue() == StatusCodes.Bad_Timeout) {
            recreate(subscription);
        }
    }

    @Override
    public void onPublishFailure(UaException exception) {
        log.info("onPublishFailure event with exception ", exception);
    }

    @Override
    public void onNotificationDataLost(UaSubscription subscription) {
        log.info("onNotificationDataLost event for {} ", subscription.toString());
    }

    private void recreate(UaSubscription subscription) {
        log.info("Initiate subscription recreation attempts until canceled.");
        try {
            subscriptionRetryTemplate.execute(retryContext -> {
                numRetry.set(retryContext.getRetryCount());
                if (controller.wasStopped()) {
                    log.info("Controller was stopped, cease subscription recreation attempts.");
                } else {
                    try {
                        controller.recreateSubscription(subscription).get();
                    } catch (InterruptedException e) {
                        log.info("Controller was stopped, cease subscription recreation attempts.");
                        Thread.currentThread().interrupt();
                    }
                }
                return null;
            });
        } catch (ExecutionException e) {
            log.error("Should not be reachable! Is Retry configured correctly?", e.getCause());
        }
        if (!Thread.currentThread().isInterrupted()) {
            numRetry.set(0);
            log.info("Connection established successfully!");
        }
    }
}