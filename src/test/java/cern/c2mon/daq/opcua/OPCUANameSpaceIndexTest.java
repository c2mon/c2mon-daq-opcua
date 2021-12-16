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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import cern.c2mon.daq.opcua.OPCUANameSpaceIndex.NamespaceInfo;

public class OPCUANameSpaceIndexTest {
    
    /**
     * This test us unchanged compared to the initial version expecting a ":" as separator, we keep it to ensure
     * that things are still working as for the first version. For more complicated cases, see the test below
     */
    @Test
    public void testS7NamespaceResolution() {
        OPCUANameSpaceIndex index = OPCUANameSpaceIndex.get();
        index.addEntry("S7:", 1);
        index.addEntry("beck:", 2);

        // Valid mappings to the S7 namespace, returning 1 in our test data
        Assertions.assertEquals(1, index.getNamespace("S7:P4US45VENT1.DB51.10,r").getId());
        Assertions.assertEquals(1, index.getNamespace("s7:P4US45VENT1.DB51.10,r").getId());
        Assertions.assertEquals(1, index.getNamespace("S7:").getId());
        Assertions.assertEquals(2, index.getNamespace("BECK:P4US45VENT1.DB51.10,r").getId());

        // missing column, or nothing after the column will not map , expected return is 0
        Assertions.assertEquals(-1, index.getNamespace("S7").getId());
        Assertions.assertEquals(-1, index.getNamespace("XXX:Whatever comes after").getId());
        Assertions.assertEquals(-1, index.getNamespace(null).getId());
        Assertions.assertEquals(-1, index.getNamespace("").getId());
        Assertions.assertEquals(-1, index.getNamespace("    ").getId());

    }
    
    /**
     * Try to check what happens with other situations ...
     */
    @Test
    public void testOtherProtocolNamespaceResolution() {
        OPCUANameSpaceIndex index = OPCUANameSpaceIndex.get();
        index.addEntry("S7:", 1);
        index.addEntry("beck:", 2);
        index.addEntry("A very long namespace", 3);
        index.addEntry("A very long namespace without sep", 4);        

        NamespaceInfo namespace1 = index.getNamespace("A very long namespace P4US45VENT1.DB51.10,r ");
        Assertions.assertEquals(3, namespace1.getId());
        Assertions.assertEquals("P4US45VENT1.DB51.10,r", namespace1.getEffectiveItemName());

        NamespaceInfo namespace2 = index.getNamespace("A very long namespace without sep   P4US45VENT1.DB51.10,r ");
        Assertions.assertEquals(4, namespace2.getId());
        Assertions.assertEquals("P4US45VENT1.DB51.10,r", namespace2.getEffectiveItemName());
        
        // No namespace match? return everyhing as it is ?
        NamespaceInfo namespace3 = index.getNamespace("A very long non matching namespace   P4US45VENT1.DB51.10,r ");
        Assertions.assertEquals(-1, namespace3.getId());
        Assertions.assertEquals("A very long non matching namespace   P4US45VENT1.DB51.10,r ", namespace3.getEffectiveItemName());

        NamespaceInfo namespace4 = index.getNamespace(null);
        Assertions.assertEquals(-1, namespace4.getId());
        Assertions.assertEquals(null, namespace4.getEffectiveItemName());

        NamespaceInfo namespace5 = index.getNamespace(" ");
        Assertions.assertEquals(-1, namespace5.getId());
        Assertions.assertEquals(" ", namespace5.getEffectiveItemName());
        
        // Just the namespace and no item?
        NamespaceInfo namespace6 = index.getNamespace("A very long namespace            ");
        Assertions.assertEquals(3, namespace6.getId());
        Assertions.assertEquals("", namespace6.getEffectiveItemName());

    }


}

