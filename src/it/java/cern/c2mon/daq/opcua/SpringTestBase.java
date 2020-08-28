package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.scope.EquipmentScope;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest
@ExtendWith(SpringExtension.class)
public class SpringTestBase {

    @Autowired
    public ApplicationContext ctx;

    private EquipmentScope s = null;

    public void setUp() throws ConfigurationException {
        System.out.println("Setup ctx");
        if (s == null) {
            s = ctx.getBean(EquipmentScope.class);
            ((ConfigurableBeanFactory) ctx.getAutowireCapableBeanFactory()).registerScope("equipment", s);
        }
    }
}
