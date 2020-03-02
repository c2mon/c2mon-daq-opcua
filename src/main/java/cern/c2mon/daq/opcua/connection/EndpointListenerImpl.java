package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class EndpointListenerImpl implements EndpointListener {

    IEquipmentMessageSender sender;

    /**
     * Implementation of the IOPCEndpointListener interface. The endpoint
     * controller will forward updates to the core (EquipmentMessageSender).
     *
     */

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
}
