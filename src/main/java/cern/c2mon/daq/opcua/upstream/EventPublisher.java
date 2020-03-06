package cern.c2mon.daq.opcua.upstream;

import cern.c2mon.daq.opcua.mapping.DataQualityMapper;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.NoArgsConstructor;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

import java.util.concurrent.ConcurrentLinkedQueue;

@NoArgsConstructor
public class EventPublisher {

    ConcurrentLinkedQueue<TagListener> tagListeners = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<EquipmentStateListener> stateListeners = new ConcurrentLinkedQueue<>();

    public void subscribeToTagEvents (final TagListener listener) {
        tagListeners.add(listener);
    }

    public void subscribeToEquipmentStateEvents (final EquipmentStateListener listener) {
        stateListeners.add(listener);
    }

    public void unsubscribeFromTagEvents (final TagListener listener) {
        tagListeners.remove(listener);
    }

    public void unsubscribeFromEquipmentStateEvents (final EquipmentStateListener listener) {
        stateListeners.remove(listener);
    }

    public void invalidTag(ISourceDataTag tag) {
        for (TagListener listener : tagListeners) {
            SourceDataTagQualityCode tagQuality = DataQualityMapper.getBadNodeIdCode();
            listener.onTagInvalid(tag, new SourceDataTagQuality(tagQuality));
        }
    }

    private void itemChange(ISourceDataTag tag, final DataValue value) {
        for (TagListener listener : tagListeners) {
            SourceDataTagQualityCode tagQuality = DataQualityMapper.getDataTagQualityCode(value.getStatusCode());
            ValueUpdate valueUpdate = new ValueUpdate(value, value.getSourceTime().getUtcTime());
            listener.onNewTagValue(tag, valueUpdate, new SourceDataTagQuality(tagQuality));
        }
    }

    public void notifyTagEvent (StatusCode statusCode, ISourceDataTag tag, DataValue value) {
        if (statusCode.isBad()) {
            invalidTag(tag);
        } else {
            itemChange(tag, value);
        }
    }

    public void notifyEquipmentState(EquipmentStateListener.EquipmentState state) {
        for (EquipmentStateListener listener: stateListeners ) {
            listener.update(state);
        }
    }

    public void clear() {
        tagListeners.clear();
        stateListeners.clear();
    }
}
