/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2020 CERN
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
package cern.c2mon.daq.opcua;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The TagCounter keeps track of the total number of updates for each Tag by ID.
 */
public class TagCounter {

    private String name;
    private MeterRegistry meterRegistry;
    private Map<Long, Counter> validCounter = new ConcurrentHashMap<>();

    /**
     * Creates a new TagCounter
     * @param name the name of the counter
     * @param registry the Prometheus meterRegistry to register the tag updates to
     */
    public TagCounter(String name, MeterRegistry registry) {
        this.name = name;
        this.meterRegistry = registry;
    }

    /**
     * Increments the counter value for the Tag with a given ID by 1 (starting by 0)
     * @param tagId the tagId whose function calls shall be incremented
     */
    public void incrementValid (long tagId) {
        Counter counter = validCounter.get(tagId);
        if (counter == null)  {
            counter = Counter.builder(name).tags("tag_id", String.valueOf(tagId)).register(meterRegistry);
            validCounter.put(tagId, counter);
        }
        counter.increment();
    }
}
