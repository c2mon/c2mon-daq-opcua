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
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * On OCP UA DAQ startup, this map should be filled with the namespace-name/namespace-id pairs, so that later on we can
 * easily use this information on the ItemDefinition to provide the correct namespace id based on the item-name
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
     * &#64;return <code>int</code> 0 if nothing matches, otherwise the namespace id as provided by the OPC UA server
     */
    public NamespaceInfo getNamespace(String itemName) {
        NamespaceInfo nameSpace = new NamespaceInfo(itemName);
        int maxLength = 0;
        if (itemName != null && !itemName.isEmpty()) {
            for (Entry<String, Integer> indexEntry : namespaceIndex.entrySet()) {
                if (itemName.toUpperCase().indexOf(indexEntry.getKey()) == 0) {
                    if (indexEntry.getKey().length() > maxLength) {
                        nameSpace.setId(indexEntry.getValue());
                        nameSpace.setName(indexEntry.getKey());
                        maxLength = indexEntry.getKey().length();
                    }
                }
            }
        } else {
            LOG.warn("Can not resolve null or empty itemName");
        }
        if (nameSpace.getId() == -1) {
            LOG.warn("itemName '{}' does not match any of the namespaces exposed by the OPC UA server", itemName);
        } 
        return nameSpace;
    }

    public class NamespaceInfo {

        private int id = -1;
        private String name;
        private String itemName;

        
        public NamespaceInfo(String itemName) {
            this.itemName = itemName;
        }

        public String getOriginalItemName() {
            return itemName;
        }

        /**
         * @return <code>String</code> the original item name WITHOUT the namespace name
         */
        public String getEffectiveItemName() {
            if (id >= 0) {
                String effectiveItemName = itemName.substring(name.length());
                return effectiveItemName.trim();
            }
            return itemName;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }
}
