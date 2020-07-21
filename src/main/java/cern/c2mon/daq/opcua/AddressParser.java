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

package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import io.netty.util.internal.StringUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An abstract class providing exclusively static methods to parse an address String for an EquipmentUnit defined in the
 * equipment configuration.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class AddressParser {

    private static final String URI = "URI";
    private static final String KEYSTORE = "keystore.";
    private static final String PKI = "pki.";

    private static final ConversionService converter = new DefaultConversionService();

    /**
     * Parses the C2MON equipment address String specified in the configuration into an array of addresses. If more than
     * one address is given, the servers at these addresses are considered as part of a redundant server cluster.
     * Optionally, any field in {@link AppConfigProperties} can by overridden by adding a key value pair. In this case, the field
     * and key must match exactly.
     * @param address The address String in the following form, where brackets indicate optional values:
     *                [URI=]protocol1://host1[:port1]/[path1][,protocol2://host2[:port2]/[path2]]
     *                [;optionalConfigurationProperty=value] [;keystore.optionalKeystoreProperty=value]
     *                [;pki.optionalPkiProperty=value]
     * @param config  The equipment configuration to modify in accordance with the optional configuration properties
     *                appended to the address parameter
     * @return An array of Strings for containing the URI for a server, or all servers in a redundant server cluster
     * @throws ConfigurationException in case the address String is malformed or missing required properties
     */
    public static Collection<String> parse(final String address, AppConfigProperties config) throws ConfigurationException {
        Map<String, String> properties = parsePropertiesFromString(address);
        String uri = properties.remove(URI);
        if (StringUtil.isNullOrEmpty(uri)) {
            log.error("Missing URI in equipment address string {}.", address);
            throw new ConfigurationException(ExceptionContext.URI_MISSING);
        }
        // Some hostnames contain characters not allowed in a URI, such as underscores in Windows machine hostnames.
        // Therefore, parsing is done using a regular expression rather than relying on java URI class methods.
        final Pattern opcUri = Pattern.compile("^(opc.tcp://)+(?:.[^./]+)+(?:/.*)?");
        final Collection<String> uris = Stream.of(uri.split(","))
                .map(String::trim)
                .filter(s -> opcUri.matcher(s).find())
                .collect(Collectors.toList());
        if (uris.isEmpty()) {
            throw new ConfigurationException(ExceptionContext.URI_SYNTAX);
        }
        overrideConfig(properties, config);
        return uris;
    }

    private static Map<String, String> parsePropertiesFromString(final String address) {
        Map<String, String> properties = new HashMap<>();
        String[] keyValues = address.split(";");
        for (int i = 0; i < keyValues.length; i++) {
            String keyValueString = keyValues[i];
            String[] keyValuePair = keyValueString.trim().split("=");
            if (keyValuePair.length > 1) {
                String key = keyValuePair[0].trim();
                String value = keyValuePair[1].trim();
                properties.put(key, value);
            } else if (i == 0) {
                properties.put(URI, keyValueString);
            }
        }
        return properties;
    }

    private static void overrideConfig(Map<String, String> properties, AppConfigProperties config) {
        final List<Map.Entry<String, String>> entries = setFields(getSubConfig(properties, KEYSTORE), config.getKeystore());
        entries.addAll(setFields(getSubConfig(properties, PKI), config.getPkiConfig()));
        properties.keySet().removeIf(s -> s.startsWith(KEYSTORE) | s.startsWith(PKI));
        entries.addAll(setFields(properties, config));
        if (!entries.isEmpty()) {
            log.info("Could not set fields {}. ", StringUtils.join(entries, ", "));
        }
    }

    private static Map<String, String> getSubConfig(Map<String, String> properties, String prefix) {
        return properties.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .collect(Collectors.toMap(e -> e.getKey().substring(prefix.length()), Map.Entry::getValue));
    }

    private static List<Map.Entry<String, String>> setFields(Map<String, String> properties, Object target) {
        return properties.entrySet().stream()
                .filter(e -> !setFieldSuccessful(target, e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private static boolean setFieldSuccessful(Object target, String name, String value) {
        try {
            final Field declaredField = target.getClass().getDeclaredField(name);
            if (declaredField.trySetAccessible()) {
                final Object val = converter.convert(value, declaredField.getType());
                declaredField.set(target, val);
                return true;
            }
        } catch (ClassCastException | IllegalAccessException e) {
            log.debug("Error setting field {}.", name, e);
        } catch (NoSuchFieldException e) {
            log.debug("No field with name {} exists in class {}:", name, target.getClass().getName(), e);
        }
        return false;
    }
}