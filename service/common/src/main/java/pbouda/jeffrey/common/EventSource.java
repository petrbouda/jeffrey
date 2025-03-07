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

package pbouda.jeffrey.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum EventSource {
    ASYNC_PROFILER(0, "Async-Profiler"), JDK(1, "JDK");

    private static final Logger LOG = LoggerFactory.getLogger(EventSource.class);

    private final int id;
    private final String label;

    private static final EventSource[] VALUES = values();

    EventSource(int id, String label) {
        this.id = id;
        this.label = label;
    }

    public int getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public static EventSource byId(int id) {
        for (EventSource source : VALUES) {
            if (source.id == id) {
                return source;
            }
        }

        LOG.error("Unknown EventSource: id={}", id);
        return null;
    }
}
