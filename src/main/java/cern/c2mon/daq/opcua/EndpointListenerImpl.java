package cern.c2mon.daq.opcua;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component(value = "endpointListener")
@NoArgsConstructor
@Slf4j
@Primary
public class EndpointListenerImpl implements EndpointListener {

    IEquipmentMessageSender sender;

    @Override
    public void initialize (IEquipmentMessageSender sender) {
        this.sender = sender;
    }

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
    public void onEquipmentStateUpdate(EquipmentState state) {
        if (state == EquipmentState.OK) {
            sender.confirmEquipmentStateOK(state.message);
        } else {
            sender.confirmEquipmentStateIncorrect(state.message);
        }
    }

    @Override
    public void onAlive(StatusCode statusCode) {
        log.debug("IsAlive returned status code {}", statusCode);
        if (statusCode.isGood()) {
            sender.sendSupervisionAlive();
        }
    }
}
