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

package cafe.jeffrey.tools.jfrotlp;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.profiles.v1development.Function;
import io.opentelemetry.proto.profiles.v1development.KeyValueAndUnit;
import io.opentelemetry.proto.profiles.v1development.Line;
import io.opentelemetry.proto.profiles.v1development.Link;
import io.opentelemetry.proto.profiles.v1development.Location;
import io.opentelemetry.proto.profiles.v1development.Mapping;
import io.opentelemetry.proto.profiles.v1development.ProfilesDictionary;
import io.opentelemetry.proto.profiles.v1development.Stack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds an OTLP {@link ProfilesDictionary}, deduplicating entries and maintaining the required
 * "index 0 is the zero value / null" convention for every table.
 */
final class Dictionary {

    private final List<String> strings = new ArrayList<>();
    private final Map<String, Integer> stringIndex = new HashMap<>();

    private final List<Function> functions = new ArrayList<>();
    private final Map<String, Integer> functionIndex = new HashMap<>();

    private final List<Location> locations = new ArrayList<>();
    private final Map<String, Integer> locationIndex = new HashMap<>();

    private final List<Stack> stacks = new ArrayList<>();
    private final Map<String, Integer> stackIndex = new HashMap<>();

    private final List<KeyValueAndUnit> attributes = new ArrayList<>();
    private final Map<String, Integer> attributeIndex = new HashMap<>();

    Dictionary() {
        // index 0 of every table must be the zero value so a 0 reference means "null / not set"
        strings.add("");
        stringIndex.put("", 0);
        functions.add(Function.getDefaultInstance());
        locations.add(Location.getDefaultInstance());
        stacks.add(Stack.getDefaultInstance());
        attributes.add(KeyValueAndUnit.getDefaultInstance());
    }

    int string(String value) {
        Integer existing = stringIndex.get(value);
        if (existing != null) {
            return existing;
        }
        int index = strings.size();
        strings.add(value);
        stringIndex.put(value, index);
        return index;
    }

    int function(String className, String methodName) {
        String key = className + '\0' + methodName;
        Integer existing = functionIndex.get(key);
        if (existing != null) {
            return existing;
        }
        String qualifiedName = className.isEmpty() ? methodName : className + '.' + methodName;
        int index = functions.size();
        functions.add(Function.newBuilder()
                .setNameStrindex(string(qualifiedName))
                .build());
        functionIndex.put(key, index);
        return index;
    }

    int location(int functionIndexRef, long line, int frameTypeAttrIndex) {
        String key = functionIndexRef + "|" + line + "|" + frameTypeAttrIndex;
        Integer existing = locationIndex.get(key);
        if (existing != null) {
            return existing;
        }
        Line.Builder lineBuilder = Line.newBuilder().setFunctionIndex(functionIndexRef);
        if (line > 0) {
            lineBuilder.setLine(line);
        }
        int index = locations.size();
        locations.add(Location.newBuilder()
                .addLines(lineBuilder)
                .addAttributeIndices(frameTypeAttrIndex)
                .build());
        locationIndex.put(key, index);
        return index;
    }

    int stack(List<Integer> locationIndices) {
        String key = locationIndices.toString();
        Integer existing = stackIndex.get(key);
        if (existing != null) {
            return existing;
        }
        int index = stacks.size();
        stacks.add(Stack.newBuilder().addAllLocationIndices(locationIndices).build());
        stackIndex.put(key, index);
        return index;
    }

    int stringAttribute(String key, String value) {
        String cacheKey = key + '\0' + value;
        Integer existing = attributeIndex.get(cacheKey);
        if (existing != null) {
            return existing;
        }
        int index = attributes.size();
        attributes.add(KeyValueAndUnit.newBuilder()
                .setKeyStrindex(string(key))
                .setValue(AnyValue.newBuilder().setStringValue(value))
                .build());
        attributeIndex.put(cacheKey, index);
        return index;
    }

    ProfilesDictionary build() {
        return ProfilesDictionary.newBuilder()
                .addAllStringTable(strings)
                // one zero-value mapping is enough — this converter does not track binary mappings
                .addMappingTable(Mapping.getDefaultInstance())
                .addAllLocationTable(locations)
                .addAllFunctionTable(functions)
                .addLinkTable(Link.getDefaultInstance())
                .addAllAttributeTable(attributes)
                .addAllStackTable(stacks)
                .build();
    }

    int stackCount() {
        return stacks.size() - 1;
    }

    int functionCount() {
        return functions.size() - 1;
    }

    int locationCount() {
        return locations.size() - 1;
    }
}
