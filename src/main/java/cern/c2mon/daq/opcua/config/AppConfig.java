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

    @Bean
    public MBeanExporter mBeanExporter() {
        MBeanExporter exporter = new AnnotationMBeanExporter();
        exporter.setAutodetect(false);
        exporter.setEnsureUniqueRuntimeObjectNames(false);
        return exporter;
    }

    /**
     * A retry template to repeatedly execute a call until successful termination with a delay starting at retryDelay
     * and increasing by a factor of 2 on every failed attempt up to a maximum of maxFailoverDelay.
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
