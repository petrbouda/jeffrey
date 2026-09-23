/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.shared.common.Json;
import tools.jackson.core.type.TypeReference;
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


    private Map<String, FlagDescription> loadDescriptions() {
        try (InputStream is = getClass().getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                LOG.warn("JVM flags description resource not found: {}", RESOURCE_PATH);
                return new ConcurrentHashMap<>();
            }

            Map<String, FlagDescription> loaded = Json.mapper().readValue(
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
