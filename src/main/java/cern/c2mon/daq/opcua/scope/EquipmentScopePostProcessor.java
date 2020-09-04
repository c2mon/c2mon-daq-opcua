package cern.c2mon.daq.opcua.scope;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Auto-detected by the Spring application context. Modified the Bean definitions appropriately annotated as {@link
 * EquipmentScoped} to be included in the {@link EquipmentScope}.
 */
@Slf4j
public class EquipmentScopePostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory (ConfigurableListableBeanFactory factory) {
        factory.registerScope("equipment", new EquipmentScope());
    }
}
