package cern.c2mon.daq.opcua;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Handles communication with the DAQ Core's {@link IEquipmentMessageSender}
 */
@Component(value = "messageSender")
@NoArgsConstructor
@Slf4j
@Primary
public class OPCUAMessageSender implements MessageSender {

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
}
