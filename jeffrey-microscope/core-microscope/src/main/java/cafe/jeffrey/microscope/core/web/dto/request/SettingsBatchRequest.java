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

package cafe.jeffrey.microscope.core.web.dto.request;

import java.util.List;

/**
 * Saves several settings in one request.
 * <p>
 * The settings page saves a whole tab at once. Sending the fields as one batch keeps them validated
 * and applied together, instead of racing as separate requests that could each rebuild derived state
 * from a partially updated configuration.
 *
 * @param items the settings to write
 */
public record SettingsBatchRequest(List<Item> items) {

    public SettingsBatchRequest {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("At least one setting is required");
        }
        items = List.copyOf(items);
    }

    /**
     * @param category logical grouping the setting belongs to
     * @param name     full property name
     * @param value    plaintext value
     * @param secret   whether the value must be encrypted at rest
     */
    public record Item(String category, String name, String value, boolean secret) {

        public Item {
            if (category == null || category.isBlank()) {
                throw new IllegalArgumentException("Setting category must not be blank");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Setting name must not be blank");
            }
            if (value == null) {
                throw new IllegalArgumentException("Setting value must not be null: name=" + name);
            }
        }
    }
}
