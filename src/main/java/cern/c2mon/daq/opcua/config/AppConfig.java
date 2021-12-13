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
package cern.c2mon.daq.opcua.config;

import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.scope.EquipmentScopePostProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.jmx.export.annotation.AnnotationMBeanExporter;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring configuration class providing different Spring Retry templates to be used throughout the OPC UA DAQ.
 */
@EnableRetry
@Configuration
@RequiredArgsConstructor
public class AppConfig {
    /**
     * Used by Spring during scoping to inject the EquipmentScope.
     * @return the Equipment-specific BeanFactoryPostProcessor handing the EquipmentScope
     */
    @Bean
    public BeanFactoryPostProcessor beanFactoryPostProcessor () {
        return new EquipmentScopePostProcessor();
    }

    /**
     * Publishes the properly annotated classes, methods and fields via JMX. The beans are registered to the exporter
     * when created within a new {@link cern.c2mon.daq.opcua.scope.EquipmentScope} instead of detected manually, where
     * they are also named according to the respective equipment.
     * @return the exporter for JMX supervision
     */
    @Bean
    public MBeanExporter mBeanExporter () {
        MBeanExporter exporter = new AnnotationMBeanExporter();
        exporter.setAutodetect(false);
        exporter.setEnsureUniqueRuntimeObjectNames(false);
        return exporter;
    }

    /**
     * A retry template to repeatedly execute a call until successful termination with a delay starting at retryDelay
     * and increasing by a factor of 2 on every failed attempt up to a maximum of maxFailoverDelay.
     * @param properties The AppConfigProperties which shall be used to create the retry template.
     * @return the retry template.
     */
    @Bean
    public RetryTemplate simpleRetryPolicy (AppConfigProperties properties) {
        RetryTemplate template = new RetryTemplate();
        template.setBackOffPolicy(backOff(properties));
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new ConcurrentHashMap<>();
        retryableExceptions.put(CommunicationException.class, true);
        SimpleRetryPolicy policy = new SimpleRetryPolicy(properties.getMaxRetryAttempts(), retryableExceptions);
        template.setRetryPolicy(policy);
        return template;
    }

    /**
     * A retry template to repeatedly execute a call until successful termination with a delay starting at retryDelay
     * and increasing by a factor of 2 on every failed attempt up to a maximum of maxFailoverDelay.
     * @param properties The AppConfigProperties which shall be used to create the retry template.
     * @return the retry template.
     */
    @Bean
    public RetryTemplate alwaysRetryTemplate (AppConfigProperties properties) {
        RetryTemplate template = new RetryTemplate();
        template.setBackOffPolicy(backOff(properties));
        template.setRetryPolicy(new AlwaysRetryPolicy());
        return template;
    }

    /**
     * A retry template to execute a call until successful termination with a delay starting at retryDelay and
     * increasing by a factor of 2 on every failed attempt up to a maximum of maxFailoverDelay. Calls are retried only
     * when they fail with a {@link CommunicationException}.
     * @param properties The AppConfigProperties which shall be used to create the retry template.
     * @return the retry template.
     */
    @Bean
    public RetryTemplate exceptionClassifierTemplate (AppConfigProperties properties) {
        RetryTemplate template = new RetryTemplate();
        template.setBackOffPolicy(backOff(properties));
        final NeverRetryPolicy neverRetry = new NeverRetryPolicy();
        final Map<Class<? extends Throwable>, RetryPolicy> policyMap = new ConcurrentHashMap<>();
        policyMap.put(CommunicationException.class, new AlwaysRetryPolicy());
        policyMap.put(OPCUAException.class, neverRetry);
        final ExceptionClassifierRetryPolicy retryPolicy = new ExceptionClassifierRetryPolicy();
        retryPolicy.setPolicyMap(policyMap);
        template.setRetryPolicy(retryPolicy);
        return template;
    }

    private BackOffPolicy backOff (AppConfigProperties properties) {
        ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
        backoff.setMaxInterval(properties.getMaxRetryDelay());
        backoff.setInitialInterval(properties.getRetryDelay());
        backoff.setMultiplier(properties.getRetryMultiplier());
        return backoff;
    }
}
