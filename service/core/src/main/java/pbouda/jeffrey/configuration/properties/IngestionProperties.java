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

package pbouda.jeffrey.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties("jeffrey.ingestion")
public class IngestionProperties {

    private Map<String, String> persistence = new HashMap<>();
    private Map<String, String> reader = new HashMap<>();

    public Map<String, String> getReader() {
        return reader;
    }

    public void setReader(Map<String, String> reader) {
        this.reader = reader;
    }

    public Map<String, String> getPersistence() {
        return persistence;
    }

    public void setPersistence(Map<String, String> persistence) {
        this.persistence = persistence;
    }
}
