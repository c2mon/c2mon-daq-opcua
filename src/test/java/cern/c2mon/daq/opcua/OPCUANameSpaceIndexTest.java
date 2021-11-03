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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

public class OPCUANameSpaceIndexTest {
        
    @Test
    public void testNamespaceResolution() {
        OPCUANameSpaceIndex index = OPCUANameSpaceIndex.get();
        index.addEntry("S7:", 1);
        index.addEntry("beck:", 2);
        
        // Valid mappings to the S7 namespace, returning 1 in our test data
        Assertions.assertEquals(1, index.getIdByItemName("S7:P4US45VENT1.DB51.10,r"));
        Assertions.assertEquals(1, index.getIdByItemName("s7:P4US45VENT1.DB51.10,r"));
        Assertions.assertEquals(1, index.getIdByItemName("S7:"));
        Assertions.assertEquals(2, index.getIdByItemName("BECK:P4US45VENT1.DB51.10,r"));
        
        // missing column, or nothing after the column will not map , expected return is 0
        Assertions.assertEquals(0, index.getIdByItemName("S7"));
        Assertions.assertEquals(0, index.getIdByItemName("XXX:Whatever comes after"));
        Assertions.assertEquals(0, index.getIdByItemName(null));
        Assertions.assertEquals(0, index.getIdByItemName(""));
        Assertions.assertEquals(0, index.getIdByItemName("    "));

    }

}
