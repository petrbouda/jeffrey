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

package pbouda.jeffrey.jfrparser.jdk;

import pbouda.jeffrey.common.Type;

import java.util.List;
import java.util.Objects;

public abstract class SingleEventProcessor implements EventProcessor {

    private final List<Type> eventTypes;

    public SingleEventProcessor(Type eventType) {
        Objects.requireNonNull(eventType);
        this.eventTypes = List.of(eventType);
    }

    public ProcessableEvents processableEvents() {
        return new ProcessableEvents(eventTypes);
    }

    protected Type eventType() {
        return eventTypes.getFirst();
    }
}
