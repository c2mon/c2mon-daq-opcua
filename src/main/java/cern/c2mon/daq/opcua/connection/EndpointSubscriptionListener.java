package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.control.Controller;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscriptionManager;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

@Slf4j
@AllArgsConstructor
public class EndpointSubscriptionListener implements UaSubscriptionManager.SubscriptionListener {

    Controller controller;

    /**
     * Subscriptions have a keep-alive counter that counts the number of consecutive publishing cycles in which there
     * have been no Notifications to report to the Client. onKeepAlive is called when the maximum keep-alive count is
     * reached to inform the Client that the Subscription is still active.
     * The maximum keep-alive count is set by the Client during Subscription creation.
     * TODO: should this be specified in the C2MON EquipmentAddress?
     *
     * @param subscription The subscription whose keep-alive counter has hit the maximum keep-alive count since the
     *                     last Notification
     * @param publishTime
     */
    @Override
    public void onKeepAlive (UaSubscription subscription, DateTime publishTime) {
        log.info("OnKeepAlive event for {}", subscription.toString());
    }

    /**
     * The client must send PublishRequests to the server to enable to send PublishResponses back. These are used to
     * deliver the notification to the client.
     * A Subscription closes automatically when there has been no processing of a Publish request nor Service calls
     * using the SubscriptionId of the Subscription within the number of consecutive publishing cycles specified by the
     * lifetime counter of the Subscription.
     * In this case, onStatusChange is called with the status code Bad_Timeout. The subscription's monitored items are
     * deleted by the server when the subscription times out.
     * The other status changes don't need to be handled specifically (CLOSED, CREATING, NORMAL, LATE, KEEPALIVE)
     * @param subscription
     * @param status
     */
    @Override
    public void onStatusChanged (UaSubscription subscription, StatusCode status) {
        log.info("onStatusChanged event for {}: {}", subscription.toString(), status.toString());
        if (status.isBad()) {
            controller.recreateSubscription(subscription);
        }
    }

    @Override
    public void onPublishFailure (UaException exception) {
        log.error("onPublishFailure event", exception);
    }

    @Override
    public void onNotificationDataLost (UaSubscription subscription) {
        log.info("onNotificationDataLost event for {}", subscription.toString());
    }

    /**
     * If a Subscription is transferred to another Session, the queued Notification Messages for this subscription are
     * moved from the old to the new subscription.
     * @param subscription
     * @param statusCode
     */
    @Override
    public void onSubscriptionTransferFailed (UaSubscription subscription, StatusCode statusCode) {
        log.info("onSubscriptionTransferFailed event for {} : StatusCode {}", subscription.toString(), statusCode);
    }
}