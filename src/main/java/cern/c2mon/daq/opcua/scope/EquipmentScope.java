package cern.c2mon.daq.opcua.scope;

import cern.c2mon.daq.common.EquipmentMessageHandler;
import cern.c2mon.daq.opcua.OPCUAMessageHandler;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.MiloEndpoint;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
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
    private static String packageName = "cern.c2mon.daq.opcua";

    private final Map<String, Object> scopedObjects = new ConcurrentHashMap<>();
    private final Map<String, Runnable> destructionCallbacks = new ConcurrentHashMap<>();

    @Setter
    private MBeanExporter exporter;

    @Setter
    private String equipmentName;


    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        if (!scopedObjects.containsKey(name)) {
            Object o = objectFactory.getObject();
            if (exporter != null && o.getClass().isAnnotationPresent(ManagedResource.class)) {
                log.info("Register as MBean: {} for equipment {}", o.getClass().getName(), equipmentName);
                    try {
                        ObjectName objName = new ObjectName(packageName + ":equipment=" + equipmentName + ",bean=" + o.getClass().getSimpleName());
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

    public boolean contains(Object o) {
        return scopedObjects.containsValue(o);
    }

}
