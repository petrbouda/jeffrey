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

package pbouda.jeffrey.jfrparser.api.record;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.type.JfrClass;
import pbouda.jeffrey.jfrparser.api.type.JfrEventType;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;

import java.time.Duration;
import java.time.Instant;

public record SimpleRecord(
        Type type,
        Instant timestamp,
        Duration timestampFromStart,
        JfrEventType eventType,
        JfrStackTrace stackTrace,
        JfrThread thread,
        JfrClass weightEntity,
        long samples,
        long sampleWeight,
        ObjectNode jsonFields) implements StackBasedRecord {
}
