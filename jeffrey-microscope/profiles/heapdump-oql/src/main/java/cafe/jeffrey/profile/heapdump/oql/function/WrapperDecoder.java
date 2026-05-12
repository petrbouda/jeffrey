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
package cafe.jeffrey.profile.heapdump.oql.function;

import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decodes the boxed value of a common JDK wrapper instance — {@code Integer},
 * {@code Long}, {@code Short}, {@code Byte}, {@code Boolean}, {@code Character},
 * {@code Float}, {@code Double}, {@code Class}. Used by the generalised
 * {@code toString(o)} so users don't have to navigate {@code o.value} by
 * hand for the common cases.
 */
public final class WrapperDecoder {

    /** Class names whose canonical wrapped value lives in a field called {@code value}. */
    private static final Set<String> VALUE_FIELD_WRAPPERS = Set.of(
            "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
            "java.lang.Boolean", "java.lang.Character", "java.lang.Float", "java.lang.Double"
    );

    private static final String CLASS_TYPE = "java.lang.Class";

    private WrapperDecoder() {
    }

    /**
     * Returns the wrapped scalar for a wrapper-class instance, or empty when
     * the instance isn't a recognised wrapper or has no decodable value.
     */
    public static Optional<Object> decodeWrapper(HeapView view, long instanceId, JavaClassRow clazz) throws SQLException {
        if (clazz == null) {
            return Optional.empty();
        }
        String name = clazz.name();
        if (CLASS_TYPE.equals(name)) {
            // Heap dumps don't reliably expose Class.name; fall back to the
            // class descriptor for the wrapped type when one of our analyzers
            // can resolve it. For now return the class's own name as the
            // canonical String form.
            return Optional.of(name);
        }
        if (!VALUE_FIELD_WRAPPERS.contains(name)) {
            return Optional.empty();
        }
        List<InstanceFieldValue> fields = view.readInstanceFields(instanceId);
        for (InstanceFieldValue f : fields) {
            if ("value".equals(f.name())) {
                return Optional.ofNullable(f.value());
            }
        }
        return Optional.empty();
    }
}
