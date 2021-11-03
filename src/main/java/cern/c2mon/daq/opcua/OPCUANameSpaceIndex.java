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

public class OPCUANameSpaceIndex {

    HashMap<String, Integer> namespaceIndex = new HashMap<>();

    private static OPCUANameSpaceIndex singleton;

    private OPCUANameSpaceIndex() {
        //
    }

    public static OPCUANameSpaceIndex get() {
        if (singleton == null) {
            singleton = new OPCUANameSpaceIndex();
        }
        return singleton;
    }

    public void addEntry(String name, int id) {
        namespaceIndex.put(name, id);
    }

    public int getIdByItemName(String itemName) {
        if (itemName != null && !itemName.isEmpty()) {
            int namespaceEndIdx = itemName.indexOf(":");
            if (namespaceEndIdx > 0) {
                String namespaceName = itemName.substring(0, namespaceEndIdx);
                return namespaceIndex.get(namespaceName);
            }
        }
        return 0;
    }

}
