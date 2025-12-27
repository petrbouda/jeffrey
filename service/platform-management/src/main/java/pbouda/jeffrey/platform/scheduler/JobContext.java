/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.platform.scheduler;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Context object for job execution containing runtime parameters.
 * Used to pass dynamic data to jobs during on-demand execution.
 */
public record JobContext(Map<String, String> parameters) {

    /**
     * Empty context for default/periodic execution.
     */
    public static final JobContext EMPTY = new JobContext(Map.of());

    public JobContext {
        parameters = parameters == null ? Map.of() : Collections.unmodifiableMap(parameters);
    }

    /**
     * Creates a context with a single parameter.
     */
    public static JobContext of(String key, String value) {
        return new JobContext(Map.of(key, value));
    }

    /**
     * Creates a context with the given parameters.
     */
    public static JobContext of(Map<String, String> params) {
        return new JobContext(params);
    }

    /**
     * Gets a parameter value by key.
     */
    public Optional<String> get(String key) {
        return Optional.ofNullable(parameters.get(key));
    }

    /**
     * Checks if a parameter exists.
     */
    public boolean hasParameter(String key) {
        return parameters.containsKey(key);
    }

    /**
     * Returns true if this is an empty context (default execution).
     */
    public boolean isEmpty() {
        return parameters.isEmpty();
    }
}
