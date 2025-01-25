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

package pbouda.jeffrey.persistence.profile.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;

import java.util.ArrayList;
import java.util.List;

public class DbJfrStackTrace implements JfrStackTrace {

    private final ArrayNode frames;

    public DbJfrStackTrace(String frames) {
        this.frames = Json.readToArrayNode(frames);
    }

    @Override
    public List<? extends JfrStackFrame> frames() {
        List<JfrStackFrame> frames = new ArrayList<>();
        for (JsonNode frame : this.frames) {
            ArrayNode arrayNode = (ArrayNode) frame;
            try {
                DbJfrStackFrame stackFrame = new DbJfrStackFrame(
                        new DbJfrMethod(
                                new DbJfrClass(arrayNode.get(0).asText()),
                                arrayNode.get(1).asText()
                        ),
                        arrayNode.get(2).asText(),
                        arrayNode.get(3).asInt(),
                        arrayNode.get(4).asInt()
                );
                frames.add(stackFrame);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return frames;
    }

    @Override
    public String toString() {
        return frames.toString();
    }
}
