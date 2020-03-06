package cern.c2mon.daq.opcua.upstream;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class EndpointListener implements TagListener, EquipmentStateListener {
    IEquipmentMessageSender sender;

    @Override
    public void onNewTagValue(final ISourceDataTag dataTag, final ValueUpdate valueUpdate, final SourceDataTagQuality quality) {
        this.sender.update(dataTag.getId(), valueUpdate, quality);

        if (log.isDebugEnabled()) {
            log.debug("onNewTagValue - Tag value " + valueUpdate + " sent for Tag #" + dataTag.getId());
        }
    }

    @Override
    public void onTagInvalid (ISourceDataTag dataTag, final SourceDataTagQuality quality) {
        this.sender.update(dataTag.getId(), quality);

        if (log.isDebugEnabled()) {
            log.debug("onTagInvalid - sent for Tag #" + dataTag.getId());
        }
    }

    @Override
    public void update (EquipmentState state) {
        if (state == EquipmentState.OK) {
            sender.confirmEquipmentStateOK(state.message);
        } else {
            sender.confirmEquipmentStateIncorrect(state.message);
        }
    }

}
