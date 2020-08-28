package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.scope.EquipmentScoped;
import lombok.RequiredArgsConstructor;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.springframework.beans.factory.FactoryBean;

@EquipmentScoped
@RequiredArgsConstructor
public class ControllerFactory implements FactoryBean<ConcreteController> {

    private final AppConfigProperties config;

    public ConcreteController getObject(AppConfigProperties.FailoverMode mode) {
        return mode.equals(AppConfigProperties.FailoverMode.NONE) ? new NoFailover() : new ColdFailover(config);
    }

    public ConcreteController getObject(RedundancySupport redundancySupport) {
            switch (redundancySupport) {
                case None:
                case Transparent:
                    return getObject(AppConfigProperties.FailoverMode.NONE);
                default:
                    return getObject(AppConfigProperties.FailoverMode.COLD);
            }
    }


    @Override
    public boolean isSingleton() {
        return false;
    }

    @Override
    public ConcreteController getObject() throws Exception {
        return new NoFailover();
    }

    @Override
    public Class<?> getObjectType() {
        return ConcreteController.class;
    }
}
