package cern.c2mon.daq.opcua;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/**
 * Handles communication with the DAQ Core's {@link IEquipmentMessageSender}
 */
@Component(value = "messageSender")
@NoArgsConstructor
@Slf4j
@Primary
@ManagedResource
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
    @ManagedOperation
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

    /**
     * Send a CommfaultTag update as an actuator operation.
     * @param state The EquipmentState to send with the Commfault. The value 'OK' will send set the CommfaultTag to
     *              false, any other value will set it to true.
     */
    @ManagedOperation(description = "Manually send a CommfaultTag. The value 'OK' will send set the CommfaultTag to false, any other value will set it to true.")
    public void onEquipmentStateUpdate(String state) {
        if (state.equalsIgnoreCase(EquipmentState.OK.name())) {
            sender.confirmEquipmentStateOK(state);
        } else {
            sender.confirmEquipmentStateIncorrect(state);
        }
    }
}
