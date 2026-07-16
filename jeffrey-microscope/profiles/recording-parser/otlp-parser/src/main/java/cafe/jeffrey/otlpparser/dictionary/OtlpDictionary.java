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
