/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2020 CERN
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
package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.scope.EquipmentScoped;
import lombok.RequiredArgsConstructor;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.retry.support.RetryTemplate;

/**
 * A Spring-managed factory to create ConcreteControllers depending on the configured {@link
 * cern.c2mon.daq.opcua.config.AppConfigProperties.FailoverMode} or on the server's {@link RedundancySupport}
 */
@EquipmentScoped
@RequiredArgsConstructor
public class ControllerFactory implements FactoryBean<ConcreteController> {

    private final AppConfigProperties configProperties;
    private final RetryTemplate alwaysRetryTemplate;

    /**
     * Creates and returns a new ConcreteController for the FailoverMode.  Every FailoverMode in OPC UA can fall back to
     * a "warmer" one. When new ConcreteControllers are implemented, they must be added here.
     * @param mode the FailoverMode for which a ConcreteController shall be created
     * @return the ConcreteController for the FailoverMode
     */
    public ConcreteController getObject (AppConfigProperties.FailoverMode mode) {
        return mode.equals(AppConfigProperties.FailoverMode.NONE) ? new NoFailover() : new ColdFailover(configProperties, alwaysRetryTemplate);
    }

    /**
     * Creates and returns a new ConcreteController for the RedundancySupport value. "Transparent" servers are identical
     * to single server setups to the Client.
     * @param redundancySupport The redundancy support value read from the server corresponding to the FailoverMode
     *                          created
     * @return The ConcreteController to handle the FailoverMode associated with the redundancySupport value.
     */
    public ConcreteController getObject (RedundancySupport redundancySupport) {
        switch (redundancySupport) {
            case Cold:
                return getObject(AppConfigProperties.FailoverMode.COLD);
            case Warm:
                return getObject(AppConfigProperties.FailoverMode.WARM);
            case Hot:
                return getObject(AppConfigProperties.FailoverMode.HOT);
            case HotAndMirrored:
                return getObject(AppConfigProperties.FailoverMode.HOTANDMIRRORED);
            default:
                return getObject(AppConfigProperties.FailoverMode.NONE);
        }
    }


    @Override
    public boolean isSingleton () {
        return false;
    }

    @Override
    public ConcreteController getObject () {
        return new NoFailover();
    }

    @Override
    public Class<?> getObjectType () {
        return ConcreteController.class;
    }
}
