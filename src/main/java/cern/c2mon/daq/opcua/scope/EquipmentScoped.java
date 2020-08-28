package cern.c2mon.daq.opcua.scope;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Qualifier
@Component
@Scope(value = "equipment")
@Retention(RetentionPolicy.RUNTIME)
public @interface EquipmentScoped { }
