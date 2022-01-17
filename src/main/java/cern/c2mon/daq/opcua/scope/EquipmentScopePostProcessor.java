/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2022 CERN
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
package cern.c2mon.daq.opcua.scope;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.EnableMBeanExport;

/**
 * Auto-detected by the Spring application context. Modified the Bean definitions appropriately annotated as {@link
 * EquipmentScoped} to be included in the {@link EquipmentScope}.
 */
@Slf4j
@RequiredArgsConstructor
@EnableMBeanExport
public class EquipmentScopePostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory (ConfigurableListableBeanFactory factory) {
        factory.registerScope("equipment", new EquipmentScope());
    }
}
