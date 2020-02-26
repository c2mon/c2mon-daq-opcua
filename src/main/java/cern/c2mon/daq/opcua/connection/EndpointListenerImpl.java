package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataQuality;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class EndpointListenerImpl implements EndpointListener {

    IEquipmentMessageSender sender;
    EndpointController controller;

    /**
     * Implementation of the IOPCEndpointListener interface. The endpoint
     * controller will forward updates to the core (EquipmentMessageSender).
     *
     * @param dataTag   The data tag which has a changed value.
     * @param timestamp The timestamp when the value was updated.
     * @param tagValue  The changed value.
     */

    @Override
    public void onNewTagValue(ISourceDataTag dataTag, long timestamp, Object tagValue) {
        log.debug("onNewTagValue - New Tag value received for Tag #" + dataTag.getId());

        this.sender.sendTagFiltered(dataTag, tagValue, timestamp);

        if (log.isDebugEnabled()) {
            log.debug("onNewTagValue - Tag value " + tagValue + " sent for Tag #" + dataTag.getId());
        }
    }

    /**
     * Invalidates the tag which caused an exception in an endpoint.
     *
     * @param dataTag The data tag which caused an exception.
     * @param cause   The exception which was caused in the endpoint.
     */
    @Override
    public void onTagInvalidException(final ISourceDataTag dataTag, final Throwable cause) {
        if (log.isDebugEnabled()) {
            log.debug("Tag invalid: " + cause.getClass().getSimpleName() + ": "
                    + cause.getMessage());
        }
        this.sender.sendInvalidTag(dataTag, SourceDataQuality.DATA_UNAVAILABLE, cause.getMessage());
    }


    /**
     * When this is called a serious error happened for our subscriptions. A
     * full restart is required.
     *
     * @param cause The cause of the subscription loss.
     */
    @Override
    public void onSubscriptionException(final Throwable cause) {
        log.error("Subscription failed. Restarting endpoint.", cause);
        controller.triggerEndpointRestart(cause.getMessage());
    }
}
