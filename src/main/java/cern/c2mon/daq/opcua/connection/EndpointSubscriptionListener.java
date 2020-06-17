package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscriptionManager;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This listener class is informed by the SDK regarding events on the server that require specific action on part of the
 * Endpoint.
 */
@Component(value = "endpointSubscriptionListener")
@Slf4j
public class EndpointSubscriptionListener implements UaSubscriptionManager.SubscriptionListener {

    @Autowired
    @Setter
    private Controller controller;

    /**
     * If a Subscription is transferred to another Session, the queued Notification Messages for this subscription are
     * moved from the old to the new subscription. If this process fails, the subscription must be recreated from
     * scratch for the new session.
     * @param subscription the old subscription which could not be recreated regardless of the status code.
     * @param statusCode   the code delivered by the server giving the reason of the subscription failure. It can be any
     *                     of the codes "Bad_NotImplemented", "Bad_NotSupported", "Bad_OutOfService" or
     *                     "Bad_ServiceUnsupported"
     */
    @Override
    public void onSubscriptionTransferFailed(UaSubscription subscription, StatusCode statusCode) {
        log.info("onSubscriptionTransferFailed event for {} : StatusCode {}", subscription.toString(), statusCode);
        recreate(subscription);
    }

    /**
     * When neither PublishRequests nor Service calls have been delivered to the server for a specific subscription for
     * a certain period of time, the subscription closes automatically and OnStatusChange is called with the status code
     * Bad_Timeout. The subscription's monitored items are then deleted automatically by the server. The other status
     * changes (CLOSED, CREATING, NORMAL, LATE, KEEPALIVE) don't need to be handled specifically. This event should not
     * occur regularly.
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

    /**
     * Attempt recreating the subscription periodically unless the subscription's monitored items cannot be mapped to
     * DataTags due to a configuration mismatch. Recreation attempts are discontinued when the thread is interrupted or
     * recreation finishes successfully.
     * @param subscription the subscription to recreate on the client.
     */
    private void recreate(UaSubscription subscription) {
        try {
            controller.recreateSubscription(subscription);
            log.info("Subscription successfully recreated!");
        } catch (CommunicationException e) {
            // only after Integer.MAX_VALUE retries have failed, should happen very rarely
            recreate(subscription);
        } catch (OPCUAException e) {
            log.error("Subscription recreation discontinued: ", e);
        }

    }
}