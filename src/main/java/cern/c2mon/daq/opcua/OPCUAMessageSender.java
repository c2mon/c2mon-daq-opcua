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
@Component(value = "messageSender")
@NoArgsConstructor
@Slf4j
@Primary
public class OPCUAMessageSender implements IMessageSender, SessionActivityListener {

    private IEquipmentMessageSender sender;

    @Override
    public void initialize(IEquipmentMessageSender sender) {
        this.sender = sender;
    }

    @Override
    public void onValueUpdate(long tagId, SourceDataTagQuality quality, ValueUpdate valueUpdate) {
        if (quality.isValid()) {
            this.sender.update(tagId, valueUpdate, quality);
        } else {
            onTagInvalid(tagId, quality);
        }
    }

    @Override
    public void onTagInvalid(long tagId, final SourceDataTagQuality quality) {
            this.sender.update(tagId, quality);
    }

    @Override
    public void onAlive() {
        log.info("Supervision alive!");
        sender.sendSupervisionAlive();
    }

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
