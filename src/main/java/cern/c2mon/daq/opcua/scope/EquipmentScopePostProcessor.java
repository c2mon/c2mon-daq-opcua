package cern.c2mon.daq.opcua.scope;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.EnableMBeanExport;

/**
 * Auto-detected by the Spring application context. Modified the Bean definitions appropriately annotated as {@link
 * EquipmentScoped} to be included in the {@link EquipmentScope}.
 */
@Slf4j
@RequiredArgsConstructor
@EnableMBeanExport
public class EquipmentScopePostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory (ConfigurableListableBeanFactory factory) {
        factory.registerScope("equipment", new EquipmentScope());
    }
}
