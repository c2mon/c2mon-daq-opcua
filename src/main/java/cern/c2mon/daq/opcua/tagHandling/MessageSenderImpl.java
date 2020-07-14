package cern.c2mon.daq.opcua.tagHandling;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static cern.c2mon.daq.opcua.tagHandling.MessageSender.EquipmentState.CONNECTION_LOST;
import static cern.c2mon.daq.opcua.tagHandling.MessageSender.EquipmentState.OK;

/**
 * A listener responsible to relay information regarding events on the DAQ to the IEquipmentMessageSender.
 */
@Component(value = "endpointListener")
@RequiredArgsConstructor
@Slf4j
@Primary
public class MessageSenderImpl implements MessageSender, SessionActivityListener {

    private IEquipmentMessageSender sender;

    /**
     * Initialize the EndpointListener with the IEquipmentMessageSender instance
     * @param sender the sender to notify of events
     */
    @Override
    public void initialize (IEquipmentMessageSender sender) {
        this.sender = sender;
    }

    @Override
    public void onTagInvalid(Optional<Long> tagId, final SourceDataTagQuality quality) {
        if (tagId.isPresent()) {
            this.sender.update(tagId.get(), quality);
        } else {
            log.info("Attempting to set unknown Tag invalid");
        }

    }

    @Override
    public void onValueUpdate(Optional<Long> tagId, SourceDataTagQuality quality, ValueUpdate valueUpdate) {
        if (quality.isValid() && tagId.isPresent()) {
            this.sender.update(tagId.get(), valueUpdate, quality);
        } else if (quality.isValid()) {
            log.error("Received update for unknown clientHandle.");
        } else {
            onTagInvalid(tagId, quality);
        }
    }

    @Override
    public void onAlive() {
        log.info("Supervision alive!");
        sender.sendSupervisionAlive();
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
    }
}
