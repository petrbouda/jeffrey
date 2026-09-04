/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.microscope.core.configuration.properties;

import cafe.jeffrey.shared.common.model.hub.HubAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Hubs declared statically under {@code jeffrey.microscope.hubs.*}, e.g.
 * <pre>
 * jeffrey.microscope.hubs.production.hostname=hub.example.com
 * jeffrey.microscope.hubs.production.port=443
 * </pre>
 * Configuration owns every hub declared here: {@code ConfiguredHubsReconciler} adds, updates and
 * removes rows on each startup so the registry matches this map, and the UI cannot delete them.
 * <p>
 * The prefix is {@code jeffrey.microscope} rather than {@code jeffrey.microscope.hubs} on purpose:
 * the binder appends the field name, so the latter would bind
 * {@code jeffrey.microscope.hubs.hubs.<key>}. Unknown keys under the prefix are ignored by default,
 * so the rest of the {@code jeffrey.microscope.*} namespace is unaffected.
 */
@ConfigurationProperties("jeffrey.microscope")
public class ConfiguredHubsProperties {

    /** Must mirror the annotation above; used to build property paths for error messages. */
    static final String PREFIX = "jeffrey.microscope";

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguredHubsProperties.class);

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9]");

    /**
     * Marks a hub id as configuration-owned. Also guarantees the id can never collide with the
     * UUIDv7 that {@code IDGenerator} mints for a hub added through the UI.
     */
    private static final String CONFIG_HUB_ID_PREFIX = "cfg-";

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private Map<String, HubEntry> hubs = new LinkedHashMap<>();

    public Map<String, HubEntry> getHubs() {
        return hubs;
    }

    public void setHubs(Map<String, HubEntry> hubs) {
        this.hubs = hubs;
    }

    /**
     * Validated, id-resolved view of the declarations, in declaration order.
     * <p>
     * A single malformed entry is skipped with a warning rather than failing startup: Microscope is
     * primarily a local profile viewer, and a typo in an optional hub should not cost the user
     * access to their recordings. Genuine ambiguity is different — two entries that would fight
     * over the same identity or the same address have no correct resolution, so they throw.
     */
    public List<DesiredHub> resolve() {
        List<DesiredHub> resolved = new ArrayList<>();
        Map<String, String> keysById = new HashMap<>();
        Map<String, String> keysByAddress = new HashMap<>();

        for (Map.Entry<String, HubEntry> entry : hubs.entrySet()) {
            String key = entry.getKey();
            HubEntry declared = entry.getValue();
            if (declared == null) {
                LOG.warn("Ignoring hub declaration with no properties: property={}", propertyPath(key));
                continue;
            }

            String invalidReason = declared.validate(key);
            if (invalidReason != null) {
                LOG.warn("Ignoring invalid hub declaration: key={} reason={}", key, invalidReason);
                continue;
            }

            String hubId = hubIdFor(key);
            String clashingKey = keysById.put(hubId, key);
            if (clashingKey != null) {
                throw new IllegalArgumentException(
                        "Hub keys resolve to the same identity: keys=[%s, %s] hub_id=%s. Keys are compared ignoring case and punctuation, so they must differ by more than a dash or an underscore."
                                .formatted(clashingKey, key, hubId));
            }

            HubAddress address = new HubAddress(
                    declared.getHostname().trim(), declared.getPort(), declared.isPlaintext());
            // Keyed on hostname:port alone, matching the UNIQUE (hostname, port) constraint on the
            // hubs table — two entries differing only in `plaintext` would still collide there.
            String clashingAddressKey = keysByAddress.put(
                    address.hostname() + ":" + address.port(), key);
            if (clashingAddressKey != null) {
                throw new IllegalArgumentException(
                        "Duplicate hub address in configuration: address=%s keys=[%s, %s]"
                                .formatted(address, clashingAddressKey, key));
            }

            resolved.add(new DesiredHub(key, hubId, declared.resolveName(key), address));
        }

        return resolved;
    }

    /**
     * Derives a stable hub id from the map key.
     * <p>
     * Stripping punctuation is not cosmetic: an environment variable cannot carry a dash, so Spring
     * binds {@code JEFFREY_MICROSCOPE_HUBS_PRODEU_...} as the key {@code prodeu} while a properties
     * file would spell the same hub {@code prod-eu}. Normalising means both resolve to the same id,
     * so moving a configuration from a file to a container does not silently orphan the
     * {@code origin.hubId} tags already written onto downloaded recordings.
     */
    public static String hubIdFor(String key) {
        return CONFIG_HUB_ID_PREFIX
                + NON_ALPHANUMERIC.matcher(key).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static String propertyPath(String key) {
        return PREFIX + ".hubs." + key;
    }

    /**
     * One resolved declaration: what the registry should contain for this key.
     */
    public record DesiredHub(String key, String hubId, String name, HubAddress address) {
    }

    /**
     * A JavaBean rather than a record on purpose — the relaxed binder resolves nested map values
     * through {@code JavaBeanBinder}, which needs a no-arg constructor and setters.
     */
    public static class HubEntry {

        private static final int DEFAULT_PORT = 9090;

        private String name;
        private String hostname;
        private int port = DEFAULT_PORT;
        private boolean plaintext;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isPlaintext() {
            return plaintext;
        }

        public void setPlaintext(boolean plaintext) {
            this.plaintext = plaintext;
        }

        /**
         * @return why this entry is unusable, or {@code null} when it is valid
         */
        String validate(String key) {
            if (hostname == null || hostname.isBlank()) {
                return propertyPath(key) + ".hostname is required";
            }
            if (port < MIN_PORT || port > MAX_PORT) {
                return "%s.port=%d must be between %d and %d"
                        .formatted(propertyPath(key), port, MIN_PORT, MAX_PORT);
            }
            return null;
        }

        /**
         * Falls back to the map key, so a hostname on its own is a complete declaration.
         */
        String resolveName(String key) {
            if (name == null || name.isBlank()) {
                return key;
            }
            return name.trim();
        }
    }
}
