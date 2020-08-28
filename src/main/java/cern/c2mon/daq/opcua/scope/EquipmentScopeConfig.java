package cern.c2mon.daq.opcua.scope;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.OPCUAMessageSender;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.config.UriModifier;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.MiloEndpoint;
import cern.c2mon.daq.opcua.connection.RetryDelegate;
import cern.c2mon.daq.opcua.connection.SecurityModule;
import cern.c2mon.daq.opcua.control.*;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionManager;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.security.CertificateGenerator;
import cern.c2mon.daq.opcua.security.CertificateLoader;
import cern.c2mon.daq.opcua.security.Certifier;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.taghandling.AliveWriter;
import cern.c2mon.daq.opcua.taghandling.CommandTagHandler;
import cern.c2mon.daq.opcua.taghandling.DataTagChanger;
import cern.c2mon.daq.opcua.taghandling.DataTagHandler;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class EquipmentScopeConfig {

//    @Bean
    public CustomScopeConfigurer customScope(EquipmentScope equipmentScope) {
        CustomScopeConfigurer c = new CustomScopeConfigurer();
        c.addScope("equipment", equipmentScope);
        return c;
    }

    ApplicationContext ctx;

    @Scope(scopeName = "equipment")
    @Bean
    public MessageSender messageSender() {
        return new OPCUAMessageSender();
    }

    // CONFIG

    @Scope(scopeName = "equipment")
    public AppConfigProperties appConfigProperties() {
//        return ctx.getBean(AppConfigProperties.class);
        return AppConfigProperties.builder().build();
    }

    @Scope(scopeName = "equipment")
    @Bean
    public UriModifier uriModifier() {
        return new UriModifier(appConfigProperties());
    }

    // CONNECTION

    @Scope(scopeName = "prototype")
    @Bean
    public SecurityModule getSecurityModule() {
        Certifier loader = new CertificateLoader(appConfigProperties().getKeystore(), appConfigProperties().getPkiConfig());
        Certifier generator = new CertificateGenerator(appConfigProperties());
        Certifier noSecurity = new NoSecurityCertifier();
        return new SecurityModule(appConfigProperties(), uriModifier(), loader, generator, noSecurity);
    }

    @Scope(scopeName = "prototype")
    @Bean
    public Endpoint miloEndpoint() {
        return new MiloEndpoint(getSecurityModule(), retryDelegate(), tagSubscriptionManager(), messageSender(), appConfigProperties());
    }

    @Scope(scopeName = "equipment")
    public RetryDelegate retryDelegate() {
        return new RetryDelegate(appConfigProperties());
    }

    // CONTROL

    @Scope(scopeName = "equipment")
    @Bean
    public Controller controller() {
        return new ControllerProxy(ctx, appConfigProperties(), miloEndpoint());
    }

    @Scope(scopeName = "equipment")
    @Bean
    public ConcreteController failover(FailoverMode.Type type) {
        switch (type) {
            case NONE:
                return new NoFailover();
            default:
            return new ColdFailover(appConfigProperties());
        }
    }

    // MAPPING

    @Scope(scopeName = "equipment")
    @Bean
    public TagSubscriptionManager tagSubscriptionManager() {
        return new TagSubscriptionMapper();
    }

    // TAG HANDLING

    @Scope(scopeName = "equipment")
    @Bean
    public AliveWriter aliveWriter() {
        return new AliveWriter(controller(), messageSender());
    }

    @Scope(scopeName = "equipment")
    @Bean
    public CommandTagHandler commandTagHandler() {
        return new CommandTagHandler(controller());
    }

    @Scope(scopeName = "equipment")
    @Bean
    public DataTagHandler dataTagHandler() {
        return new DataTagHandler(tagSubscriptionManager(), messageSender(), controller());
    }

    @Scope(scopeName = "equipment")
    @Bean
    public DataTagChanger dataTagChanger() {
        return new DataTagChanger(dataTagHandler());
    }

}
