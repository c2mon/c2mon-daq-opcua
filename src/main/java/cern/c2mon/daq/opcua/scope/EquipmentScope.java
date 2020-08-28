package cern.c2mon.daq.opcua.scope;

import cern.c2mon.daq.common.EquipmentMessageHandler;
import cern.c2mon.daq.opcua.OPCUAMessageHandler;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.MiloEndpoint;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defines a custom EquipmentScope to hold all beans in cern.c2mon.daq.opcua with the exception of {@link MiloEndpoint}
 * and {@link AppConfigProperties}. These beans should be shared across one instance of the {@link OPCUAMessageHandler},
 * but be unique across two {@link OPCUAMessageHandler}s. Since the {@link EquipmentMessageHandler}s are initialized
 * manually be the DAQ Core and are not managed by Spring, the {@link EquipmentScope} should not be registered at
 * startup!
 */
@Component("equipmentBeanProxy")
@org.springframework.context.annotation.Scope(value = "prototype")
public class EquipmentScope implements Scope {

    private final Map<String, Object> scopedObjects = new ConcurrentHashMap<>();
    private final Map<String, Runnable> destructionCallbacks = new ConcurrentHashMap<>();

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        if (!scopedObjects.containsKey(name)) {
            scopedObjects.put(name, objectFactory.getObject());
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
        return null;
    }

}
