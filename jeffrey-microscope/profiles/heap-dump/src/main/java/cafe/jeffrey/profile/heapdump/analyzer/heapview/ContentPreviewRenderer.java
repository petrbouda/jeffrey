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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;

/**
 * Renders a human-readable preview of an instance's content — the decoded
 * value for known types, or a short hex dump as a safe fallback.
 *
 * <p>Decoded types:
 * <ul>
 *   <li>{@code java.lang.String} → quoted decoded text via
 *       {@link JavaStringDecoder}.</li>
 *   <li>Boxed primitive wrappers ({@code Long}, {@code Integer}, {@code Short},
 *       {@code Byte}, {@code Float}, {@code Double}, {@code Character},
 *       {@code Boolean}) → {@code String.valueOf} on the {@code value} field.</li>
 *   <li>{@code java.math.BigDecimal} (compact path, i.e. fits in a {@code long})
 *       — decoded from {@code intCompact + scale}. The INFLATED path renders
 *       as a stub since it requires walking the backing {@code BigInteger.mag}
 *       array.</li>
 *   <li>{@code java.util.UUID} — decoded from {@code mostSigBits / leastSigBits}.</li>
 *   <li>{@code java.time.Instant}, {@code java.time.Duration} — decoded from
 *       {@code seconds + nanos}.</li>
 *   <li>{@code java.time.LocalDate}, {@code java.time.LocalTime} — decoded
 *       from their primitive year/month/day or hour/minute/second/nano fields.</li>
 * </ul>
 *
 * Any decode failure (missing field, malformed value, SQL error) falls through
 * to the safety net: first 16 bytes of the raw field block as space-separated
 * hex. The hex form is always a valid output, so a weird class layout never
 * breaks the report.
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
            String formatted = renderTyped(view, className, exemplarInstanceId);
            if (formatted != null) {
                return formatted;
            }
        } catch (SQLException ignored) {
            // Fall through to hex — the report stays valid even on weird layouts.
        }
        return hexPreview(exemplarBytes);
    }

    private static String renderTyped(HeapView view, String className, long instanceId) throws SQLException {
        if ("java.lang.String".equals(className)) {
            return renderString(view, instanceId);
        }
        if (BOXED_WRAPPERS.contains(className)) {
            return renderBoxedValue(view, instanceId);
        }
        return switch (className) {
            case "java.math.BigDecimal" -> capRaw(renderBigDecimal(view, instanceId));
            case "java.util.UUID" -> capRaw(renderUuid(view, instanceId));
            case "java.time.Instant" -> capRaw(renderInstant(view, instanceId));
            case "java.time.Duration" -> capRaw(renderDuration(view, instanceId));
            case "java.time.LocalDate" -> capRaw(renderLocalDate(view, instanceId));
            case "java.time.LocalTime" -> capRaw(renderLocalTime(view, instanceId));
            default -> null;
        };
    }

    private static String renderString(HeapView view, long instanceId) throws SQLException {
        Optional<JavaStringDecoder.Decoded> decoded = JavaStringDecoder.decode(view, instanceId);
        return decoded.map(d -> quoteAndCap(d.content())).orElse(null);
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

    private static String renderBigDecimal(HeapView view, long instanceId) throws SQLException {
        Long intCompact = null;
        Integer scale = null;
        Long intValId = null;
        for (InstanceFieldValue f : view.readInstanceFields(instanceId)) {
            switch (f.name()) {
                case "intCompact" -> {
                    if (f.value() instanceof Long v) intCompact = v;
                }
                case "scale" -> {
                    if (f.value() instanceof Integer v) scale = v;
                }
                case "intVal" -> {
                    if (f.value() instanceof Long ref) intValId = ref;
                }
                default -> { /* ignore */ }
            }
        }
        if (intCompact == null || scale == null) {
            return null;
        }
        // INFLATED sentinel = Long.MIN_VALUE. The real value lives in the
        // BigInteger pointed to by intVal, which requires walking the mag[]
        // primitive-int-array — deferred. Render a clear stub instead of hex.
        if (intCompact == Long.MIN_VALUE) {
            return intValId != null && intValId != 0L ? "BigDecimal (large)" : null;
        }
        return new BigDecimal(BigInteger.valueOf(intCompact), scale).toPlainString();
    }

    private static String renderUuid(HeapView view, long instanceId) throws SQLException {
        Long msb = null;
        Long lsb = null;
        for (InstanceFieldValue f : view.readInstanceFields(instanceId)) {
            switch (f.name()) {
                case "mostSigBits" -> {
                    if (f.value() instanceof Long v) msb = v;
                }
                case "leastSigBits" -> {
                    if (f.value() instanceof Long v) lsb = v;
                }
                default -> { /* ignore */ }
            }
        }
        if (msb == null || lsb == null) {
            return null;
        }
        return new UUID(msb, lsb).toString();
    }

    private static String renderInstant(HeapView view, long instanceId) throws SQLException {
        Long seconds = null;
        Integer nanos = null;
        for (InstanceFieldValue f : view.readInstanceFields(instanceId)) {
            switch (f.name()) {
                case "seconds" -> {
                    if (f.value() instanceof Long v) seconds = v;
                }
                case "nanos" -> {
                    if (f.value() instanceof Integer v) nanos = v;
                }
                default -> { /* ignore */ }
            }
        }
        if (seconds == null || nanos == null) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(seconds, nanos).toString();
        } catch (DateTimeException | ArithmeticException e) {
            return null;
        }
    }

    private static String renderDuration(HeapView view, long instanceId) throws SQLException {
        Long seconds = null;
        Integer nanos = null;
        for (InstanceFieldValue f : view.readInstanceFields(instanceId)) {
            switch (f.name()) {
                case "seconds" -> {
                    if (f.value() instanceof Long v) seconds = v;
                }
                case "nanos" -> {
                    if (f.value() instanceof Integer v) nanos = v;
                }
                default -> { /* ignore */ }
            }
        }
        if (seconds == null || nanos == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(seconds, nanos).toString();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private static String renderLocalDate(HeapView view, long instanceId) throws SQLException {
        Integer year = null;
        Short month = null;
        Short day = null;
        for (InstanceFieldValue f : view.readInstanceFields(instanceId)) {
            switch (f.name()) {
                case "year" -> {
                    if (f.value() instanceof Integer v) year = v;
                }
                case "month" -> {
                    if (f.value() instanceof Short v) month = v;
                }
                case "day" -> {
                    if (f.value() instanceof Short v) day = v;
                }
                default -> { /* ignore */ }
            }
        }
        if (year == null || month == null || day == null) {
            return null;
        }
        try {
            return LocalDate.of(year, month, day).toString();
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static String renderLocalTime(HeapView view, long instanceId) throws SQLException {
        Byte hour = null;
        Byte minute = null;
        Byte second = null;
        Integer nano = null;
        for (InstanceFieldValue f : view.readInstanceFields(instanceId)) {
            switch (f.name()) {
                case "hour" -> {
                    if (f.value() instanceof Byte v) hour = v;
                }
                case "minute" -> {
                    if (f.value() instanceof Byte v) minute = v;
                }
                case "second" -> {
                    if (f.value() instanceof Byte v) second = v;
                }
                case "nano" -> {
                    if (f.value() instanceof Integer v) nano = v;
                }
                default -> { /* ignore */ }
            }
        }
        if (hour == null || minute == null || second == null || nano == null) {
            return null;
        }
        try {
            return LocalTime.of(hour, minute, second, nano).toString();
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** Cap unquoted previews at MAX_LEN; preserves a single-char ellipsis on overflow. */
    private static String capRaw(String s) {
        if (s == null) {
            return null;
        }
        if (s.length() <= MAX_LEN) {
            return s;
        }
        return s.substring(0, MAX_LEN - 1) + "…";
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
