package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.mapping.DataQualityMapper;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
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

    public void invalidTag(ISourceDataTag tag) {
        for (EndpointListener listener : listeners) {
            SourceDataTagQualityCode tagQuality = DataQualityMapper.getBadNodeIdCode();
            listener.onTagInvalid(tag, new SourceDataTagQuality(tagQuality));
        }
    }

    private void itemChange(ISourceDataTag tag, SourceDataTagQualityCode tagQuality, final ValueUpdate value) {
        for (EndpointListener listener : listeners) {
            listener.onNewTagValue(tag, value, new SourceDataTagQuality(tagQuality));
        }
    }

    public void notifyTagEvent (ISourceDataTag tag, SourceDataTagQualityCode tagQuality, ValueUpdate value) {
        if (!tagQuality.equals(SourceDataTagQualityCode.OK)) {
            invalidTag(tag);
        } else {
            itemChange(tag, tagQuality, value);
        }
    }

    public void notifyAlive (StatusCode statusCode) {
        listeners.forEach(l -> l.onAlive(statusCode));
    }

    public void notifyEquipmentState(EndpointListener.EquipmentState state) {
        for (EndpointListener listener : listeners) {
            listener.update(state);
        }
    }

    public void clear() {
        listeners.clear();
    }
}