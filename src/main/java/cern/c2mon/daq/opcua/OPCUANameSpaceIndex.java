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

package cern.c2mon.daq.opcua;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * On OCP UA DAQ startup, this map should be filled with the namespace-name/namespace-id pairs, so that later
 * on we can easily use this information on the ItemDefinition to provide the correct namespace id based on 
 * the item-name
 * 
 * @author mbuttner
 */
public class OPCUANameSpaceIndex {

    HashMap<String, Integer> namespaceIndex = new HashMap<>();

    private static OPCUANameSpaceIndex singleton;
    private static final Logger LOG = LoggerFactory.getLogger(OPCUANameSpaceIndex.class);
    
    //
    // --- CONSTRUCTION ------------------------------------------
    //
    /**
     * Ctor is private (this is an old school singleton, to get easy access to its data
     */
    private OPCUANameSpaceIndex() {
        //
    }
    
    public static OPCUANameSpaceIndex get() {
        if (singleton == null) {
            singleton = new OPCUANameSpaceIndex();
            LOG.info("Index created  ... ");
        }
        return singleton;
    }
    
    //
    // --- PUBLIC METHODS ----------------------------------------
    //

    /**
     * Fill data on startup.
     */
    public void addEntry(String namespaceName, int namespaceId) {
        namespaceIndex.put(namespaceName.toUpperCase(), namespaceId);
        LOG.info(" - adding {} with id {}", namespaceName.toUpperCase(), namespaceId);
    }
    
    /**
     * @param itemName <code>String>/code> expected "namespaceName:rest of the name"
     * @return <code>int</code> 0 if nothing matches, otherwise the namespace id as provided by the OPC UA server
     */
    public int getIdByItemName(String itemName) {
        if (itemName != null && !itemName.isEmpty()) {
            int namespaceEndIdx = itemName.indexOf(":");
            if (namespaceEndIdx > 0) {
                String namespaceName = itemName.substring(0, namespaceEndIdx + 1).toUpperCase();
                if (namespaceIndex.containsKey(namespaceName)) {
                    return namespaceIndex.get(namespaceName);
                } else {
                    LOG.warn("namespace '{}' declared by itemName '{}' is not a domain exposed by the OPC UA server", namespaceName, itemName);                                    
                }
            } else {
                LOG.warn("itemName '{}' does not expose a namespace-name (missing ':')", itemName);                
            }
        } else {
            LOG.warn("Can not resolve null or empty itemName");
        }
        return 0;
    }

}
