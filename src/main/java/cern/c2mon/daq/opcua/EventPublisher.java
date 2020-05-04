package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.mapping.DataQualityMapper;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.NoArgsConstructor;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

@NoArgsConstructor
@Component("publisher")
public class EventPublisher {

    ConcurrentLinkedQueue<EndpointListener> listeners = new ConcurrentLinkedQueue<>();

    public void subscribe (final EndpointListener listener) {
        listeners.add(listener);
    }

    public void unsubscribe (final EndpointListener listener) {
        listeners.remove(listener);
    }

    public void invalidTag(Long tagId) {
        for (EndpointListener listener : listeners) {
            SourceDataTagQualityCode tagQuality = DataQualityMapper.getBadNodeIdCode();
            listener.onTagInvalid(tagId, new SourceDataTagQuality(tagQuality));
        }
    }

    private void itemChange(Long tagId, SourceDataTagQualityCode tagQuality, final ValueUpdate value) {
        for (EndpointListener listener : listeners) {
            listener.onNewTagValue(tagId, value, new SourceDataTagQuality(tagQuality));
        }
    }

    public void notifyTagEvent (Long tagId, SourceDataTagQualityCode tagQuality, ValueUpdate value) {
        if (!tagQuality.equals(SourceDataTagQualityCode.OK)) {
            invalidTag(tagId);
        } else {
            itemChange(tagId, tagQuality, value);
        }
    }

    public void notifyAlive (StatusCode statusCode) {
        listeners.forEach(l -> l.onAlive(statusCode));
    }

    public void notifyEquipmentState(EndpointListener.EquipmentState state) {
        for (EndpointListener listener : listeners) {
            listener.onEquipmentStateUpdate(state);
        }
    }

    public void clear() {
        listeners.clear();
    }
}
