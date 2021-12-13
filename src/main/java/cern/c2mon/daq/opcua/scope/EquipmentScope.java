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
package cern.c2mon.daq.opcua.scope;

import cern.c2mon.daq.common.EquipmentMessageHandler;
import cern.c2mon.daq.opcua.OPCUAMessageHandler;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.MiloEndpoint;
import cern.c2mon.daq.opcua.metrics.MetricProxy;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.context.ApplicationContext;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defines a custom EquipmentScope to hold all beans in cern.c2mon.daq.opcua with the exception of {@link MiloEndpoint}
 * and {@link AppConfigProperties}. These beans should be shared across one instance of the {@link OPCUAMessageHandler},
 * but be unique across two {@link OPCUAMessageHandler}s. Since the {@link EquipmentMessageHandler}s are initialized
 * manually be the DAQ Core and are not managed by Spring, the {@link EquipmentScope} should not be registered at
 * startup!
 */
@Component("equipmentScope")
@org.springframework.context.annotation.Scope(value = "prototype")
@Slf4j
public class EquipmentScope implements Scope {
    private static String domain = "cern.c2mon.daq.opcua";
    private final Map<String, Object> scopedObjects = new ConcurrentHashMap<>();
    private final Map<String, Runnable> destructionCallbacks = new ConcurrentHashMap<>();

    @Setter
    private MBeanExporter exporter;

    private String equipmentName;

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        if (!scopedObjects.containsKey(name)) {
            Object o = objectFactory.getObject();
            if (exporter != null && o.getClass().isAnnotationPresent(ManagedResource.class)) {
                log.info("Register as MBean: {} for equipment {}", o.getClass().getName(), equipmentName);
                try {
                    ObjectName objName = new ObjectName(domain + ":equipment=" + equipmentName + ",bean=" + o.getClass().getSimpleName());
                    log.info("Registering bean {} with name {}", o, objName.toString());
                    exporter.registerManagedResource(o, objName);
                    log.info("Registered the bean {} of class {} under {}", o, o.getClass().getName(), objName.toString());
                } catch (MalformedObjectNameException e) {
                    log.error("Could not register the bean as MBean: ", e);
                }
            } else if (equipmentName != null) {
                log.info("Get Object of Class {} for equipment {}", o.getClass().getName(), equipmentName);
            }
            scopedObjects.put(name, o);
        }
        return scopedObjects.get(name);
    }

    @Override
    public Object remove(String name) {
        destructionCallbacks.remove(name);
        return scopedObjects.remove(name);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        destructionCallbacks.put(name, callback);
    }

    @Override
    public Object resolveContextualObject(String s) {
        return null;
    }

    @Override
    public String getConversationId() {
        return equipmentName;
    }

    /**
     * Each EquipmentScope is associated with an Equipment with a given name. To map the metrics supervised within the
     * DAQ to a given Equipment name, the name is set during initization of the scope to the MetricProxy tasked with
     * instrumentation and monitoring.
     * @param equipmentName the Equipment associated with the scope
     * @param equipmentId   the ID of the Equipment associated with the scope
     * @param processName   the Process associated with the scope
     * @param processId     the ID of the Process associated with the scope
     * @param context       the current ApplicationContext
     */
    public void initialize(String equipmentName, Long equipmentId, String processName, Long processId, ApplicationContext context) {
        log.info("Initialize scope for Process {} and Equipment {}", processName, equipmentName);
        MetricProxy metricProxy = context.getBean(MetricProxy.class);
        metricProxy.addDefaultTags(
                "equipment_name", equipmentName,
                "process_name", processName,
                "equipment_id", String.valueOf(equipmentId),
                "process_id", String.valueOf(processId));
        this.equipmentName = equipmentName;
    }
}
