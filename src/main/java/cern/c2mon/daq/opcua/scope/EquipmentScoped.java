package cern.c2mon.daq.opcua.scope;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A composite annotation for Beans which call be included in the {@link EquipmentScope}.
 */
@Qualifier
@Component
@Scope(value = "equipment", proxyMode=ScopedProxyMode.NO)
@Retention(RetentionPolicy.RUNTIME)
public @interface EquipmentScoped { }
