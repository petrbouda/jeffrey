/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.otlpparser.dictionary;

import io.opentelemetry.proto.profiles.v1development.KeyValueAndUnit;
import io.opentelemetry.proto.profiles.v1development.Link;
import io.opentelemetry.proto.profiles.v1development.Location;
import io.opentelemetry.proto.profiles.v1development.Mapping;
import io.opentelemetry.proto.profiles.v1development.ProfilesDictionary;
import io.opentelemetry.proto.profiles.v1development.Stack;
import io.opentelemetry.proto.profiles.v1development.Function;

/**
 * Index-aware view over an OTLP {@code ProfilesDictionary}.
 * <p>
 * Every dictionary table follows the OTLP convention that <em>index 0 is the zero value</em> and an
 * index of {@code 0} means "null / not set". The accessors therefore return {@code null} (or an empty
 * string for the string table) for index {@code 0} and for out-of-range indices, so callers never
 * have to re-implement the bounds/zero handling.
 */
public final class OtlpDictionary {

    private final ProfilesDictionary dictionary;

    public OtlpDictionary(ProfilesDictionary dictionary) {
        this.dictionary = dictionary;
    }

    /**
     * @return the referenced string, or an empty string for the null index ({@code 0}) and
     * out-of-range indices
     */
    public String string(int index) {
        if (index <= 0 || index >= dictionary.getStringTableCount()) {
            return "";
        }
        return dictionary.getStringTable(index);
    }

    /**
     * @return the referenced mapping, or {@code null} for the null index and out-of-range indices
     */
    public Mapping mapping(int index) {
        if (index <= 0 || index >= dictionary.getMappingTableCount()) {
            return null;
        }
        return dictionary.getMappingTable(index);
    }

    /**
     * @return the referenced location, or {@code null} for the null index and out-of-range indices
     */
    public Location location(int index) {
        if (index <= 0 || index >= dictionary.getLocationTableCount()) {
            return null;
        }
        return dictionary.getLocationTable(index);
    }

    /**
     * @return the referenced function, or {@code null} for the null index and out-of-range indices
     */
    public Function function(int index) {
        if (index <= 0 || index >= dictionary.getFunctionTableCount()) {
            return null;
        }
        return dictionary.getFunctionTable(index);
    }

    /**
     * @return the referenced stack, or {@code null} for the null index and out-of-range indices
     */
    public Stack stack(int index) {
        if (index <= 0 || index >= dictionary.getStackTableCount()) {
            return null;
        }
        return dictionary.getStackTable(index);
    }

    /**
     * @return the referenced link, or {@code null} for the null index and out-of-range indices
     */
    public Link link(int index) {
        if (index <= 0 || index >= dictionary.getLinkTableCount()) {
            return null;
        }
        return dictionary.getLinkTable(index);
    }

    /**
     * @return the referenced attribute, or {@code null} for the null index and out-of-range indices
     */
    public KeyValueAndUnit attribute(int index) {
        if (index <= 0 || index >= dictionary.getAttributeTableCount()) {
            return null;
        }
        return dictionary.getAttributeTable(index);
    }
}
