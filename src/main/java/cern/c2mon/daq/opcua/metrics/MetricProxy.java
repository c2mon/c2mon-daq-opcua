package cern.c2mon.daq.opcua.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component(value = "metricmanager")
@Getter
public class MetricProxy {
    private final MeterRegistry meterRegistry;

    private TagCounter validTagCounter;
    private TagCounter invalidTagCounter;

    public MetricProxy(@Autowired MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        validTagCounter = new TagCounter("valid_tag_counter", meterRegistry);
        invalidTagCounter = new TagCounter("invalid_tag_counter", meterRegistry);
    }
}
