package cern.c2mon.daq.opcua;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import static cern.c2mon.daq.opcua.EndpointListener.EquipmentState.CONNECTION_LOST;
import static cern.c2mon.daq.opcua.EndpointListener.EquipmentState.OK;

/**
 * A listener responsible to relay information from the OPC UA server to the IEquipmentMessageSender.
 */
@Component(value = "endpointListener")
@NoArgsConstructor
@Slf4j
@Primary
public class EndpointListenerImpl implements EndpointListener, SessionActivityListener {

    private IEquipmentMessageSender sender;

    /**
     * Initialize the EndpointListener with the IEquipmentMessageSender instance
     * @param sender the sender to notify of events
     */
    @Override
    public void initialize (IEquipmentMessageSender sender) {
        this.sender = sender;
    }

    /**
     * Called a subscription delivers a new value for a node corresponding to a given tagId
     * @param tagId the tagId for which a new value has arrived
     * @param valueUpdate the new value wrapped in a valueUpdate
     * @param quality the data quality of the new value
     */
    @Override
    public void onNewTagValue(final Long tagId, final ValueUpdate valueUpdate, final SourceDataTagQuality quality) {
        this.sender.update(tagId, valueUpdate, quality);
        if (log.isDebugEnabled()) {
            log.debug("onNewTagValue - Tag value " + valueUpdate + " sent for Tag #" + tagId);
        }
    }

    @Override
    public void onTagInvalid (Long tagId, final SourceDataTagQuality quality) {
        this.sender.update(tagId, quality);
        if (log.isDebugEnabled()) {
            log.debug("onTagInvalid - sent for Tag #" + tagId);
        }

    }

    @Override
    public void onAlive(StatusCode statusCode) {
        if (statusCode.isGood()) {
            sender.sendSupervisionAlive();
        }
        if (log.isDebugEnabled()) {
            log.debug("IsAlive returned status code {}", statusCode);
        }
    }

    @Override
    public void onSessionActive(UaSession session) {
        onEquipmentStateUpdate(OK);
    }

    @Override
    public void onSessionInactive(UaSession session) {
        onEquipmentStateUpdate(CONNECTION_LOST);
    }

    @Override
    public void onEquipmentStateUpdate(EquipmentState state) {
        if (state == EquipmentState.OK) {
            sender.confirmEquipmentStateOK(state.message);
        } else {
            sender.confirmEquipmentStateIncorrect(state.message);
        }
        if (log.isDebugEnabled()) {
            log.debug("EquipmentStatueUpdate returned state {}", state);
        }
    }
}
