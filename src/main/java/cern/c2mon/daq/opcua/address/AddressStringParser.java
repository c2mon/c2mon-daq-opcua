/******************************************************************************
 * Copyright (C) 2010-2016 CERN. All rights not expressly granted are reserved.
 *
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * C2MON is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the license.
 *
 * C2MON is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with C2MON. If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

package cern.c2mon.daq.opcua.address;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import lombok.NonNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.exceptions.ConfigurationException.Cause.*;

public class AddressStringParser {

    /**
     * Creates a properties object which has the properties defined in the
     * provided address String. Thread-safe.
     *
     * @param address The address String in the form:
     *
     *    URI=protocol1://host1[:port1]/[path1][,protocol2://host2[:port2]/[path2]];
     *    user=user1[@domain1][,user2[@domain2]];password=password1[,password2];
     *    serverTimeout=serverTimeout;serverRetryTimeout=serverRetryTimeout
     *    [;aliveWriter=true|false];redundantServerStateName=redundantServerStateName
     *
     *    The parts in brackets are optional.
     *
     * @return The EquipmentAddress object with the properties as given in the address String.
     */
    public static List<EquipmentAddress> parse(final String address) throws ConfigurationException {
        List<EquipmentAddress> addresses;
        try {
            Properties properties = parsePropertiesFromString(address);
            addresses = createAddressesFromProperties(properties);
        } catch (URISyntaxException e) {
            throw new ConfigurationException(ADDRESS_URI, e);
        }
        return addresses;
    }

    private static List<EquipmentAddress> createAddressesFromProperties(Properties properties) throws URISyntaxException, ConfigurationException {
        return EquipmentPropertyParser.of(properties).parse();
    }

    private static Properties parsePropertiesFromString(final String address) {
        Properties properties = new Properties();

        String[] keyValues = address.split(";");
        for (String keyValueString : keyValues) {
            String[] keyValuePair = keyValueString.trim().split("=");
            // if there is nothing to split ignore it
            if (keyValuePair.length > 1) {
                String key = keyValuePair[0].trim();
                String value = keyValuePair[1].trim();
                properties.put(key, value);
            }
        }
        return properties;
    }

    private static class EquipmentPropertyParser {
        public enum RequiredKeys { URI, serverTimeout, serverRetryTimeout}
        public enum OptionalKeys { user, password, aliveWriter, vendor }
        String uri;
        String usersAtDomain;
        String password;
        int serverTimeout;
        int serverRetryTimeout;
        boolean aliveWriterEnabled;

        private EquipmentPropertyParser(Properties properties) {
            this.uri = properties.getProperty(RequiredKeys.URI.name());
            this.serverTimeout = Integer.parseInt(properties.getProperty(RequiredKeys.serverTimeout.name()));
            this.serverRetryTimeout = Integer.parseInt(properties.getProperty(RequiredKeys.serverRetryTimeout.name()));
            this.usersAtDomain = properties.getProperty(OptionalKeys.user.name(), null);
            this.password = properties.getProperty(OptionalKeys.password.name(), null);
            this.aliveWriterEnabled = Boolean.parseBoolean(properties.getProperty(OptionalKeys.aliveWriter.name(), "true"));
        }

        public EquipmentPropertyParser(@NonNull String uri, String usersAtDomain, String password, @NonNull int serverTimeout, @NonNull int serverRetryTimeout, boolean aliveWriterEnabled) {
            this.uri = uri;
            this.usersAtDomain = usersAtDomain;
            this.password = password;
            this.serverTimeout = serverTimeout;
            this.serverRetryTimeout = serverRetryTimeout;
            this.aliveWriterEnabled = aliveWriterEnabled;
        }

        public static EquipmentPropertyParser of(Properties properties) throws ConfigurationException {
            checkProperties(properties);
            return new EquipmentPropertyParser(properties);
        }

        private static void checkProperties(Properties properties) throws ConfigurationException {
            ArrayList<String> propertyList = (ArrayList<String>) Collections.list(properties.propertyNames());
            Set<String> requiredKeySet = Arrays.stream(RequiredKeys.values())
                    .map(RequiredKeys::name)
                    .collect(Collectors.toSet());
            checkForRequiredKeys(propertyList, requiredKeySet);

            Set<String> optionalKeySet = Arrays.stream(OptionalKeys.values())
                    .map(OptionalKeys::name)
                    .collect(Collectors.toSet());
            checkForInvalidKeys(propertyList, requiredKeySet, optionalKeySet);
        }

        private static void checkForRequiredKeys(ArrayList<String> properties, Set<String> requiredKeySet) throws ConfigurationException {
            boolean containsRequiredKeys = properties.containsAll(requiredKeySet);
            if (!containsRequiredKeys) {
                throw new ConfigurationException(ADDRESS_MISSING_PROPERTIES);
            }
        }

        private static void checkForInvalidKeys(ArrayList<String> properties, Set<String> requiredKeySet, Set<String> optionalKeySet) throws ConfigurationException {
            properties.removeAll(requiredKeySet);
            properties.removeAll(optionalKeySet);
            boolean containsInvalidKeys = !properties.isEmpty();
            if (containsInvalidKeys) {
                throw new ConfigurationException(ADDRESS_INVALID_PROPERTIES);
            }
        }

        public List<EquipmentAddress> parse() throws URISyntaxException {
            List<EquipmentAddress> addresses = new ArrayList<>();
            for (int i = 0; i < this.uri.split(",").length; i++) {
                addresses.add(reduceToIndex(i).toEquipmentAddress());
            }
            return addresses;
        }

        private EquipmentPropertyParser reduceToIndex(int n) {
            assert  this.uri != null;
            if (this.usersAtDomain != null && this.password != null) {
                return new EquipmentPropertyParser(uri.split(",")[n].trim(),
                        usersAtDomain.split(",")[n].trim(),
                        password.split(",")[n].trim(),
                        serverTimeout, serverRetryTimeout, aliveWriterEnabled);
            }
            else return new EquipmentPropertyParser(uri.split(",")[n].trim(),
                    this.usersAtDomain, this.password, serverTimeout, serverRetryTimeout, aliveWriterEnabled);
        }

        private EquipmentAddress toEquipmentAddress() throws URISyntaxException {
            return new EquipmentAddress(new URI(this.uri), extractFirstUser(), extractFirstDomain(), this.password,
                    this.serverTimeout, this.serverRetryTimeout, this.aliveWriterEnabled);
        }

        private String extractFirstUser() {
            return containsUserAndDomain() ? this.usersAtDomain.split("@")[0] : null;
        }

        private String extractFirstDomain() {
            return containsUserAndDomain() ? this.usersAtDomain.split("@")[1] : null;
        }

        private boolean containsUserAndDomain() {
            return this.usersAtDomain != null && this.usersAtDomain.contains("@");
        }
    }
}
