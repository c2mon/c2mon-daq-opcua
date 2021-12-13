/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2021 CERN
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
package cern.c2mon.daq.opcua.metrics;

import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.scope.EquipmentScoped;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An abstraction for Micrometer {@link io.micrometer.core.instrument.Meter}s
 */
@Component(value = "metricmanager")
@RequiredArgsConstructor
@EquipmentScoped
@Slf4j
public class MetricProxy {

    private static final String PREFIX = "c2mon_daq_opcua";
    private static final String VALID_TAG_COUNTER = "tag_updates_valid";
    private static final String INVALID_TAG_COUNTER = "tag_updates_invalid";
    private static final String TAGS_PER_SUBSCRIPTION_GAUGE = "tags_per_subscription";

    private final MeterRegistry registry;
    private TagCounter validTagCounter;
    private TagCounter invalidTagCounter;

    private Tags defaultTags = Tags.empty();

    /**
     * Registers the number or Tags per subscription to be gauged.
     * @param map    the map of tags for a subscription with the given time deadband
     * @param timeDB the respective subscription's time deadband
     */
    public void initializeTagsPerSubscriptionGauge(Map<Long, ItemDefinition> map, int timeDB) {
        registry.gaugeMapSize(PREFIX + "_" + TAGS_PER_SUBSCRIPTION_GAUGE, getTags("time_deadband", String.valueOf(timeDB)), map);
        registry.get(PREFIX + "_" + TAGS_PER_SUBSCRIPTION_GAUGE).gauge();
    }

    /**
     * Register an update sent by a DataTag
     * @param valid was the update read successfully?
     * @param tagId the id of the respective DataTag
     */
    public void incrementTagCounter(boolean valid, long tagId) {
        incrementCounter(valid, String.valueOf(tagId));
    }

    /**
     * Register a sent Commfault message
     * @param valid false if the Commfault message indicates an error, true otherwise
     */
    public void incrementCommfault(boolean valid) {
        incrementCounter(valid, "commfault");
    }

    /**
     * Creates a {@link Tag} from the key and value, which will be added to every metric update.
     * @param keyValues an array of the {@link Tag} key and value pairs
     */
    public void addDefaultTags(String... keyValues) {
        defaultTags = defaultTags.and(keyValues);

        // Initialize OSHI metrics after the necessary default Tags have been added to the MetricProxy
        new NetworkMetrics(defaultTags).bindTo(registry);
    }

    private void incrementCounter(boolean valid, String id) {
        if (validTagCounter == null) {
            validTagCounter = new TagCounter(registry, PREFIX + "_" + VALID_TAG_COUNTER);
        }
        if (invalidTagCounter == null) {
            invalidTagCounter = new TagCounter(registry, PREFIX + "_" + INVALID_TAG_COUNTER);
        }
        if (valid) {
            validTagCounter.increment(id);
        } else {
            invalidTagCounter.increment(id);
        }
    }

    private Iterable<Tag> getTags(String... additional) {
        return defaultTags.and(additional);
    }

    /**
     * The TagCounter keeps track of the total number of updates for each Tag by ID.
     */
    @RequiredArgsConstructor
    private class TagCounter {

        @NonNull
        private final MeterRegistry meterRegistry;
        private final String name;
        private final Map<String, Counter> validCounter = new ConcurrentHashMap<>();

        private void increment(String tagId) {
            Counter counter = validCounter.get(tagId);
            if (counter == null) {
                counter = meterRegistry.counter(name, getTags("tag_id", tagId));
                validCounter.put(tagId, counter);
            }
            counter.increment();
        }
    }

}
