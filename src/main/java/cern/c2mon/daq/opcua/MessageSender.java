package cern.c2mon.daq.opcua;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import static cern.c2mon.daq.opcua.IMessageSender.EquipmentState.CONNECTION_LOST;
import static cern.c2mon.daq.opcua.IMessageSender.EquipmentState.OK;

/**
 * Handles communication with the DAQ Core's {@link IEquipmentMessageSender}
 */
@Component(value = "endpointListener")
@NoArgsConstructor
@Slf4j
@Primary
public class MessageSender implements IMessageSender, SessionActivityListener {

    private IEquipmentMessageSender sender;

    /**
     * Initialize the EndpointListener with the IEquipmentMessageSender instance
     * @param sender the sender to notify of events
     */
    @Override
    public void initialize(IEquipmentMessageSender sender) {
        this.sender = sender;
    }

    /**
     * Updates the value of the {@link cern.c2mon.shared.common.datatag.ISourceDataTag} with the ID tagId
     * @param tagId the id of the {@link cern.c2mon.shared.common.datatag.ISourceDataTag} whose value to update
     * @param quality the {@link SourceDataTagQuality} of the updated value
     * @param valueUpdate the {@link ValueUpdate} to send to the {@link cern.c2mon.shared.common.datatag.ISourceDataTag}
     */
    @Override
    public void onValueUpdate(long tagId, SourceDataTagQuality quality, ValueUpdate valueUpdate) {
        if (quality.isValid()) {
            this.sender.update(tagId, valueUpdate, quality);
        } else {
            onTagInvalid(tagId, quality);
        }
    }

    /**
     * Notifies the {@link IEquipmentMessageSender} that a tagId could not be subscribed, or that a bad reading was obtained
     * @param tagId the id of the {@link cern.c2mon.shared.common.datatag.ISourceDataTag} to update to invalid
     * @param quality the quality of the {@link cern.c2mon.shared.common.datatag.ISourceDataTag}
     */
    @Override
    public void onTagInvalid(long tagId, final SourceDataTagQuality quality) {
            this.sender.update(tagId, quality);
    }

    /**
     * Send an update to the configured aliveTag
     */
    @Override
    public void onAlive() {
        log.info("Supervision alive!");
        sender.sendSupervisionAlive();
    }

    /**
     * Updates the state of equipment and connection.
     * @param state the state to update
     */
    @Override
    public void onEquipmentStateUpdate(EquipmentState state) {
        if (state == EquipmentState.OK) {
            sender.confirmEquipmentStateOK(state.message);
        } else {
            sender.confirmEquipmentStateIncorrect(state.message);
        }
    }

    /**
     * Inform the {@link IEquipmentMessageSender} that the connection is healthy of a Session activates.
     * @param session the activated session
     */
    @Override
    public void onSessionActive(UaSession session) {
        onEquipmentStateUpdate(OK);
    }

    /**
     * Inform the {@link IEquipmentMessageSender} that the connection has been lost when a Session deactivates.
     * @param session the deactivated session
     */
    @Override
    public void onSessionInactive(UaSession session) {
        onEquipmentStateUpdate(CONNECTION_LOST);
    }
}
