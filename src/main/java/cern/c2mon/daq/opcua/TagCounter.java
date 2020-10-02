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

public class TagCounter {

    private String name;
    private String tagName;

    private MeterRegistry meterRegistry;
    private Map<String, Counter> counters = new ConcurrentHashMap<>();

    public TagCounter(String name, String tagName, MeterRegistry registry) {
        this.name = name;
        this.tagName = tagName;
        this.meterRegistry = registry;
    }

    public void increment(long tagValue) {
        final String tagValueString = "data-tag-id-" + tagValue;
        Counter counter = counters.get(tagValueString);
        if (counter == null)  {
            counter = Counter.builder(name).tags(tagName, tagValueString).register(meterRegistry);
            counters.put(tagValueString, counter);
        }
        counter.increment();
    }
}
