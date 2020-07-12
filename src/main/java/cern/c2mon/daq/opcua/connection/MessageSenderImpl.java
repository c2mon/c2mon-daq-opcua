package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import static cern.c2mon.daq.opcua.connection.MessageSender.EquipmentState.CONNECTION_LOST;
import static cern.c2mon.daq.opcua.connection.MessageSender.EquipmentState.OK;

/**
 * A listener responsible to relay information regarding events on the DAQ to the IEquipmentMessageSender.
 */
@Component(value = "endpointListener")
@RequiredArgsConstructor
@Slf4j
@Primary
public class MessageSenderImpl implements MessageSender, SessionActivityListener {

    private final TagSubscriptionMapper mapper;
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
    public void onTagInvalid(UInteger clientHandle, final SourceDataTagQuality quality) {
        final Long tagId = mapper.getTagId(clientHandle);
        this.sender.update(tagId, quality);

    }

    @Override
    public void onValueUpdate(UInteger clientHandle, SourceDataTagQuality quality, ValueUpdate valueUpdate) {
        if (quality.isValid()) {
            try {
                final Long tagId = mapper.getTagId(clientHandle);
                if (tagId == null) {
                    log.error("Received update for unknown clientHandle: {}", clientHandle);
                } else {
                    this.sender.update(tagId, valueUpdate, quality);
                }
            } catch (Exception e) {
                log.error("Caught for clientHandle {} with Mapper at state {}.", clientHandle, mapper.getTagIdDefinitionMap(), e);
            }
        } else {
            onTagInvalid(clientHandle, quality);
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
