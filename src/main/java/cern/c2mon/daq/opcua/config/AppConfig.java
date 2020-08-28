package cern.c2mon.daq.opcua.config;

import cern.c2mon.daq.opcua.scope.EquipmentScopePostProcessor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class AppConfig {

    @Bean
    public static BeanFactoryPostProcessor beanFactoryPostProcessor() {
        return new EquipmentScopePostProcessor();
    }
}
