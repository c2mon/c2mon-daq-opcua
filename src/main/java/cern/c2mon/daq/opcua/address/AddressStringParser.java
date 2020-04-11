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

import cern.c2mon.daq.opcua.address.EquipmentAddress.ServerAddress;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.exceptions.ConfigurationException.Cause.*;

/**
 * An abstract class providing exclusively static methods to parse an address String for an EquipmentUnit defined
 * in the equipment configuration.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class AddressStringParser {

    /**
     * Parses the address String into an {@link EquipmentAddress} object according to the properties defined in the String.
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
     * @return The {@link EquipmentAddress} object with the properties as given in the address String.
     */
    public static EquipmentAddress parse(final String address) throws ConfigurationException {
        try {
            Properties properties = parsePropertiesFromString(address);
            return EquipmentPropertyParser.of(properties).parse();
        } catch (URISyntaxException e) {
            throw new ConfigurationException(ADDRESS_URI, e);
        }
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
        @AllArgsConstructor
        public enum Keys {
            URI("URI", true),
            SERVER_TIMEOUT("serverTimeout", true),
            SERVER_RETRY_TIMEOUT("serverRetryTimeout", true),
            USER("user", false),
            PASSWORD("password", false),
            ALIVE_WRITER("aliveWriter", false),
            VENDOR("vendor", false);
            String propertyName;
            boolean required;
        }
        String uri;
        String userAtDomain;
        String password;
        int serverTimeout;
        int serverRetryTimeout;
        boolean aliveWriterEnabled;

        private EquipmentPropertyParser(Properties properties) {
            this.uri = properties.getProperty(Keys.URI.propertyName);
            this.serverTimeout = Integer.parseInt(properties.getProperty(Keys.SERVER_TIMEOUT.propertyName));
            this.serverRetryTimeout = Integer.parseInt(properties.getProperty(Keys.SERVER_RETRY_TIMEOUT.propertyName));
            this.userAtDomain = properties.getProperty(Keys.USER.propertyName, "");
            this.password = properties.getProperty(Keys.PASSWORD.propertyName, "");
            this.aliveWriterEnabled = Boolean.parseBoolean(properties.getProperty(Keys.ALIVE_WRITER.propertyName, "true"));
        }

        public static EquipmentPropertyParser of(Properties properties) throws ConfigurationException {
            checkProperties(properties);
            return new EquipmentPropertyParser(properties);
        }

        private static void checkProperties(Properties properties) throws ConfigurationException {
            Set<String> propertyList = new HashSet<>(properties.stringPropertyNames());
            List<Keys> keys = Arrays.asList(Keys.values());
            checkForRequiredKeys(propertyList, keys);
            checkForInvalidKeys(propertyList, keys);
        }

        private static void checkForRequiredKeys(Set<String> properties, List<Keys> keys) throws ConfigurationException {
            Set<String> requiredPropertyNames = keys.stream()
                    .filter(key -> key.required)
                    .map(key -> key.propertyName)
                    .collect(Collectors.toSet());
            if (!properties.containsAll(requiredPropertyNames)) {
                throw new ConfigurationException(ADDRESS_MISSING_PROPERTIES);
            }
        }

        private static void checkForInvalidKeys(Set<String> properties, List<Keys> keys) throws ConfigurationException {
            properties.removeAll(keys.stream().map(key -> key.propertyName).collect(Collectors.toSet()));
            if (!properties.isEmpty()) {
                throw new ConfigurationException(ADDRESS_INVALID_PROPERTIES);
            }
        }

        public EquipmentAddress parse() throws URISyntaxException, ConfigurationException {
            return new EquipmentAddress(parseServerAddresses(), serverTimeout, serverRetryTimeout, aliveWriterEnabled);
        }

        private List<ServerAddress> parseServerAddresses() throws URISyntaxException {
            List<ServerAddress> addresses = new ArrayList<>();

            String[] uris = splitBy(this.uri, ",");
            String[] usersAtDomain = splitBy(this.userAtDomain, ",");
            String[] passwords = splitBy(this.password, ",");

            for (int i = 0; i < uris.length; i++) {
                if (i < usersAtDomain.length && i < passwords.length) {
                    addresses.add(new ServerAddress(new URI(uris[i].trim()),
                            extractUser(usersAtDomain[i]),
                            extractDomain(usersAtDomain[i]),
                            passwords[i].trim()));
                }
                else {
                    addresses.add(new ServerAddress(new URI(uris[i].trim())));
                }
            }
            return addresses;
        }

        private static String[] splitBy (String stringToSplit, String delimiter) {
            return stringToSplit.split(delimiter);
        }

        private static String extractUser (String userAtDomain) {
            return getUserAtDomainElement(userAtDomain, 0);
        }

        private static String extractDomain (String userAtDomain) {
            return getUserAtDomainElement(userAtDomain, 1);
        }

        private static String getUserAtDomainElement(String userAtDomain, int i) {
            return (userAtDomain != null && userAtDomain.contains("@")) ? splitBy(userAtDomain, "@")[i].trim() : null;
        }
    }
}
