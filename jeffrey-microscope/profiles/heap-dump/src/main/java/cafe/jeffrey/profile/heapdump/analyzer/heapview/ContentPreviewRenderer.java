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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;

/**
 * Renders a human-readable preview of an instance's content for the
 * "Content Preview" column of the Duplicated Objects report.
 *
 * <ul>
 *   <li>{@code java.lang.String} → quoted decoded text via
 *       {@link JavaStringDecoder}, capped to {@value #MAX_LEN} chars.</li>
 *   <li>Boxed primitive wrappers ({@code java.lang.Long}, {@code Integer},
 *       {@code Short}, {@code Byte}, {@code Float}, {@code Double},
 *       {@code Character}, {@code Boolean}) → {@code String.valueOf} on
 *       the single {@code value} field.</li>
 *   <li>Anything else (or any decode failure) → first 16 bytes of the raw
 *       field block formatted as space-separated hex, identical to the
 *       previous default rendering. The hex form is always a valid output,
 *       so a malformed class layout never breaks the report.</li>
 * </ul>
 */
public final class ContentPreviewRenderer {

    private static final int MAX_LEN = 80;

    private static final Set<String> BOXED_WRAPPERS = Set.of(
            "java.lang.Long",
            "java.lang.Integer",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Float",
            "java.lang.Double",
            "java.lang.Character",
            "java.lang.Boolean");

    private ContentPreviewRenderer() {
    }

    public static String render(HeapView view, String className,
            long exemplarInstanceId, byte[] exemplarBytes) {
        try {
            if ("java.lang.String".equals(className)) {
                Optional<JavaStringDecoder.Decoded> decoded =
                        JavaStringDecoder.decode(view, exemplarInstanceId);
                if (decoded.isPresent()) {
                    return quoteAndCap(decoded.get().content());
                }
            } else if (BOXED_WRAPPERS.contains(className)) {
                String formatted = renderBoxedValue(view, exemplarInstanceId);
                if (formatted != null) {
                    return formatted;
                }
            }
        } catch (SQLException ignored) {
            // Fall through to hex — the report stays valid even on weird layouts.
        }
        return hexPreview(exemplarBytes);
    }

    private static String renderBoxedValue(HeapView view, long instanceId) throws SQLException {
        for (InstanceFieldValue f : view.readInstanceFields(instanceId)) {
            if ("value".equals(f.name())) {
                Object v = f.value();
                return v == null ? null : String.valueOf(v);
            }
        }
        return null;
    }

    private static String quoteAndCap(String s) {
        int innerCap = MAX_LEN - 2;
        if (s.length() <= innerCap) {
            return '"' + s + '"';
        }
        return '"' + s.substring(0, innerCap - 1) + "…\"";
    }

    private static String hexPreview(byte[] bytes) {
        int n = Math.min(bytes.length, 16);
        StringBuilder sb = new StringBuilder(n * 3);
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xFF));
            if (i + 1 < n) {
                sb.append(' ');
            }
        }
        if (bytes.length > 16) {
            sb.append("…");
        }
        return sb.toString();
    }
}
