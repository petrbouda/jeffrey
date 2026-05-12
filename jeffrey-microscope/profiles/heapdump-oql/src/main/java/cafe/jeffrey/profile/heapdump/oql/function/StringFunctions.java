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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.JavaStringDecoder;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Plan C string-flavoured functions: {@code toString(o)} and {@code toHex(n)}.
 * {@code toString} is generalised — for {@code java.lang.String} it decodes
 * UTF content, for {@link WrapperDecoder known wrappers} it returns the
 * boxed scalar formatted as a string, otherwise it falls back to the
 * {@code class@hex} display form used by {@code @displayName}.
 */
public final class StringFunctions {

    private static final String JAVA_LANG_STRING = "java.lang.String";

    private StringFunctions() {
    }

    public static String toStringValue(HeapView view, InstanceRow instance, JavaClassRow clazz) throws SQLException {
        if (clazz != null && JAVA_LANG_STRING.equals(clazz.name())) {
            Optional<JavaStringDecoder.Decoded> d = JavaStringDecoder.decode(view, instance.instanceId());
            if (d.isPresent()) {
                return d.get().content();
            }
        }
        if (clazz != null) {
            Optional<Object> wrapped = WrapperDecoder.decodeWrapper(view, instance.instanceId(), clazz);
            if (wrapped.isPresent()) {
                return String.valueOf(wrapped.get());
            }
            return clazz.name() + "@" + Long.toHexString(instance.instanceId());
        }
        return "@" + Long.toHexString(instance.instanceId());
    }

    public static String toHex(Object n) {
        if (n == null) {
            return null;
        }
        if (n instanceof Number num) {
            return "0x" + Long.toHexString(num.longValue());
        }
        throw new IllegalArgumentException("toHex requires a numeric argument, got " + n.getClass());
    }
}
