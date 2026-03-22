/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides descriptions for JVM flags loaded from a static JSON resource.
 * The descriptions are extracted from OpenJDK globals.hpp source files.
 */
public class JvmFlagDescriptionProvider {

    private static final Logger LOG = LoggerFactory.getLogger(JvmFlagDescriptionProvider.class);
    private static final String RESOURCE_PATH = "/jvm-flags-descriptions.json";

    private final Map<String, FlagDescription> descriptions;

    public JvmFlagDescriptionProvider() {
        this.descriptions = loadDescriptions();
    }

    /**
     * Gets the description for a JVM flag.
     *
     * @param flagName the name of the flag
     * @return the description, or null if not found
     */
    public String getDescription(String flagName) {
        FlagDescription desc = descriptions.get(flagName);
        return desc != null ? desc.description() : null;
    }

    /**
     * Gets the category for a JVM flag.
     *
     * @param flagName the name of the flag
     * @return the category, or null if not found
     */
    public String getCategory(String flagName) {
        FlagDescription desc = descriptions.get(flagName);
        return desc != null ? desc.category() : null;
    }

    /**
     * Checks if a description exists for the given flag.
     *
     * @param flagName the name of the flag
     * @return true if description exists
     */
    public boolean hasDescription(String flagName) {
        return descriptions.containsKey(flagName);
    }

    /**
     * Gets the total number of flag descriptions loaded.
     *
     * @return the count of loaded descriptions
     */
    public int getDescriptionCount() {
        return descriptions.size();
    }

    private Map<String, FlagDescription> loadDescriptions() {
        try (InputStream is = getClass().getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                LOG.warn("JVM flags description resource not found: {}", RESOURCE_PATH);
                return new ConcurrentHashMap<>();
            }

            ObjectMapper mapper = new ObjectMapper();
            Map<String, FlagDescription> loaded = mapper.readValue(
                    is,
                    new TypeReference<Map<String, FlagDescription>>() {}
            );

            LOG.info("Loaded JVM flag descriptions: count={}", loaded.size());
            return loaded;
        } catch (IOException e) {
            LOG.error("Failed to load JVM flag descriptions: error={}", e.getMessage(), e);
            return new ConcurrentHashMap<>();
        }
    }

    /**
     * Record representing a flag description entry.
     */
    public record FlagDescription(String description, String category) {}
}
