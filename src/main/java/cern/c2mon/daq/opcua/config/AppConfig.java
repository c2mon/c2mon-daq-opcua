package cern.c2mon.daq.opcua.config;

import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.scope.EquipmentScopePostProcessor;
import cern.c2mon.daq.opcua.scope.EquipmentScoped;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@EnableRetry
@Configuration
@RequiredArgsConstructor
@EquipmentScoped
public class AppConfig {
    final AppConfigProperties properties;

    @Bean
    public static BeanFactoryPostProcessor beanFactoryPostProcessor() {
        return new EquipmentScopePostProcessor();
    }

    /**
     * A retry template to repeatedly execute a call until successful termination with a delay starting at retryDelay
     * and increasing by a factor of 2 on every failed attempt up to a maximum of maxFailoverDelay.
     * @return the retry template.
     */
    @Bean
    public RetryTemplate simpleRetryPolicy() {
        RetryTemplate template = new RetryTemplate();
        template.setBackOffPolicy(backOff());
        final Map<Class<? extends Throwable>, RetryPolicy> policyMap = new ConcurrentHashMap<>();
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
    public RetryTemplate alwaysRetryTemplate() {
        RetryTemplate template = new RetryTemplate();
        template.setBackOffPolicy(backOff());
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
    public RetryTemplate exceptionClassifierTemplate() {
        RetryTemplate template = new RetryTemplate();
        template.setBackOffPolicy(backOff());
        final NeverRetryPolicy neverRetry = new NeverRetryPolicy();
        final Map<Class<? extends Throwable>, RetryPolicy> policyMap = new ConcurrentHashMap<>();
        policyMap.put(CommunicationException.class, new AlwaysRetryPolicy());
        policyMap.put(OPCUAException.class, neverRetry);
        final ExceptionClassifierRetryPolicy retryPolicy = new ExceptionClassifierRetryPolicy();
        retryPolicy.setPolicyMap(policyMap);
        template.setRetryPolicy(retryPolicy);
        return template;
    }

    private BackOffPolicy backOff() {
        ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
        backoff.setMaxInterval(properties.getMaxRetryDelay());
        backoff.setInitialInterval(properties.getRetryDelay());
        backoff.setMultiplier(properties.getRetryMultiplier());
        return backoff;
    }
}
