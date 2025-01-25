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

package pbouda.jeffrey.common.model.profile;

import com.fasterxml.jackson.databind.node.ArrayNode;
import pbouda.jeffrey.common.Json;

import java.util.List;

public record EventStacktrace(
        Long stacktraceId,
        StacktraceType type,
        List<EventFrame> frames,
        List<EventStacktraceTag> tags) {

    public EventStacktrace(Long stacktraceId, StacktraceType type, List<EventFrame> frames) {
        this(stacktraceId, type, frames, List.of());
    }

    public EventStacktrace withTags(List<EventStacktraceTag> tags) {
        return new EventStacktrace(stacktraceId, type, frames, tags);
    }

    public ArrayNode toJsonArray() {
        ArrayNode array = Json.createArray();
        for (EventFrame frame : frames) {
            ArrayNode frameInJson = frame.toJson();
            array.add(frameInJson);
        }
        return array;
    }
}
