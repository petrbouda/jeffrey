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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.InstanceFieldValue;
import cafe.jeffrey.profile.heapdump.view.InstanceRow;
import cafe.jeffrey.profile.heapdump.view.JdkFieldNames;

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

    private static final String STRING_CLASS = String.class.getName();

    /** Classes whose preview is the String their {@code name} field points at (enums too). */
    private static final Set<String> NAME_FIELD_CLASSES = Set.of("java.lang.Thread", "java.lang.Class");

    /** Classes, beyond the boxed wrappers, whose preview is decoded from their own fields. */
    private static final Set<String> FIELD_RENDERED_CLASSES = Set.of(
            "java.math.BigDecimal",
            "java.util.UUID",
            "java.time.Instant",
            "java.time.Duration",
            "java.time.LocalDate",
            "java.time.LocalTime",
            "java.lang.Thread",
            "java.lang.Class");

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
            String formatted = renderTyped(view, className, exemplarInstanceId, false);
            if (formatted != null) {
                return formatted;
            }
        } catch (SQLException ignored) {
            // Fall through to hex — the report stays valid even on weird layouts.
        }
        return hexPreview(exemplarBytes);
    }

    /**
     * Render a preview for the given instance, returning {@code null} when no known
     * layout applies. Unlike {@link #render}, this entry point never falls back to a
     * hex dump — suited for UIs that prefer "no preview" over a meaningless byte stream.
     *
     * @param isEnum {@code true} when the class transitively extends {@code java.lang.Enum};
     *               in that case the {@code name} field is resolved as the preview.
     */
    public static String renderOrNull(HeapView view, String className, long instanceId, boolean isEnum)
            throws SQLException {
        return renderTyped(view, className, instanceId, isEnum);
    }

    /**
     * Previews for a page of instances of one class, keyed by instance id; an instance with nothing
     * to show is absent. The same previews {@link #renderOrNull} gives one at a time, but the strings
     * a page needs -- the instances themselves for {@code String}, the name each one points at for
     * {@code Thread}, {@code Class} and enums -- are read in one query instead of one per instance,
     * and the fields come from rows the caller already holds.
     */
    public static Map<Long, String> renderPage(HeapView view, String className, List<InstanceRow> rows, boolean isEnum)
            throws SQLException {
        if (STRING_CLASS.equals(className)) {
            return renderStrings(view, rows.stream().map(InstanceRow::instanceId).toList());
        }
        Map<Long, String> previews = new HashMap<>();
        if (!rendersFromFields(className, isEnum)) {
            return previews;
        }
        Map<Long, List<InstanceFieldValue>> fieldsById = new LinkedHashMap<>();
        for (InstanceRow row : rows) {
            fieldsById.put(row.instanceId(), view.readInstanceFields(row));
        }
        Map<Long, String> names = namesOf(view, className, isEnum, fieldsById.values());
        for (Map.Entry<Long, List<InstanceFieldValue>> entry : fieldsById.entrySet()) {
            String preview = renderFields(className, entry.getValue(), isEnum, names);
            if (preview != null) {
                previews.put(entry.getKey(), preview);
            }
        }
        return previews;
    }

    /**
     * Previews of {@code String} instances keyed by id, their contents read in one query; a String
     * whose content was not materialised at index time is decoded from the dump on its own.
     */
    public static Map<Long, String> renderStrings(HeapView view, Collection<Long> stringIds) throws SQLException {
        Map<Long, String> contents = view.findStringContents(stringIds);
        Map<Long, String> previews = new HashMap<>();
        for (long id : stringIds) {
            String preview = renderString(view, id, Optional.ofNullable(contents.get(id)));
            if (preview != null) {
                previews.put(id, preview);
            }
        }
        return previews;
    }

    private static String renderTyped(HeapView view, String className, long instanceId, boolean isEnum)
            throws SQLException {
        if (STRING_CLASS.equals(className)) {
            return renderString(view, instanceId, view.findStringContent(instanceId));
        }
        if (!rendersFromFields(className, isEnum)) {
            return null;
        }
        List<InstanceFieldValue> fields = view.readInstanceFields(instanceId);
        return renderFields(className, fields, isEnum, namesOf(view, className, isEnum, List.of(fields)));
    }

    private static boolean rendersFromFields(String className, boolean isEnum) {
        return isEnum || BOXED_WRAPPERS.contains(className) || FIELD_RENDERED_CLASSES.contains(className);
    }

    private static boolean rendersNameField(String className, boolean isEnum) {
        return isEnum || NAME_FIELD_CLASSES.contains(className);
    }

    /** The content of every {@code name} String the instances point at, when their preview is that name. */
    private static Map<Long, String> namesOf(
            HeapView view, String className, boolean isEnum, Collection<List<InstanceFieldValue>> instances)
            throws SQLException {
        if (!rendersNameField(className, isEnum)) {
            return Map.of();
        }
        List<Long> nameRefs = new ArrayList<>();
        for (List<InstanceFieldValue> fields : instances) {
            Long ref = nameRef(fields);
            if (ref != null) {
                nameRefs.add(ref);
            }
        }
        return nameRefs.isEmpty() ? Map.of() : view.findStringContents(nameRefs);
    }

    private static String renderFields(
            String className, List<InstanceFieldValue> fields, boolean isEnum, Map<Long, String> names) {
        if (BOXED_WRAPPERS.contains(className)) {
            return renderBoxedValue(fields);
        }
        if (rendersNameField(className, isEnum)) {
            Long ref = nameRef(fields);
            return ref == null ? null : capRaw(names.get(ref));
        }
        return switch (className) {
            case "java.math.BigDecimal" -> capRaw(renderBigDecimal(fields));
            case "java.util.UUID" -> capRaw(renderUuid(fields));
            case "java.time.Instant" -> capRaw(renderInstant(fields));
            case "java.time.Duration" -> capRaw(renderDuration(fields));
            case "java.time.LocalDate" -> capRaw(renderLocalDate(fields));
            case "java.time.LocalTime" -> capRaw(renderLocalTime(fields));
            default -> null;
        };
    }

    /**
     * The {@code name} String reference on an instance (Thread, Class, Enum), or null when it has
     * none. The name is rendered unquoted because identifiers like {@code MainThread} or
     * {@code java.util.HashMap} read better without quotes.
     */
    private static Long nameRef(List<InstanceFieldValue> fields) {
        for (InstanceFieldValue f : fields) {
            if (JdkFieldNames.THREAD_NAME.equals(f.name()) && f.value() instanceof Long ref && ref != 0L) {
                return ref;
            }
        }
        return null;
    }

    private static String renderString(HeapView view, long instanceId, Optional<String> content) throws SQLException {
        if (content.isPresent()) {
            return quoteAndCap(content.get());
        }
        Optional<JavaStringDecoder.Decoded> decoded = JavaStringDecoder.decode(view, instanceId);
        return decoded.map(d -> quoteAndCap(d.content())).orElse(null);
    }

    private static String renderBoxedValue(List<InstanceFieldValue> fields) {
        for (InstanceFieldValue f : fields) {
            if (JdkFieldNames.STRING_VALUE.equals(f.name())) {
                Object v = f.value();
                return v == null ? null : String.valueOf(v);
            }
        }
        return null;
    }

    private static String renderBigDecimal(List<InstanceFieldValue> fields) {
        Long intCompact = null;
        Integer scale = null;
        Long intValId = null;
        for (InstanceFieldValue f : fields) {
            switch (f.name()) {
                case "intCompact" -> {
                    if (f.value() instanceof Long v) {
                        intCompact = v;
                    }
                }
                case "scale" -> {
                    if (f.value() instanceof Integer v) {
                        scale = v;
                    }
                }
                case "intVal" -> {
                    if (f.value() instanceof Long ref) {
                        intValId = ref;
                    }
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

    private static String renderUuid(List<InstanceFieldValue> fields) {
        Long msb = null;
        Long lsb = null;
        for (InstanceFieldValue f : fields) {
            switch (f.name()) {
                case "mostSigBits" -> {
                    if (f.value() instanceof Long v) {
                        msb = v;
                    }
                }
                case "leastSigBits" -> {
                    if (f.value() instanceof Long v) {
                        lsb = v;
                    }
                }
                default -> { /* ignore */ }
            }
        }
        if (msb == null || lsb == null) {
            return null;
        }
        return new UUID(msb, lsb).toString();
    }

    private static String renderInstant(List<InstanceFieldValue> fields) {
        Long seconds = null;
        Integer nanos = null;
        for (InstanceFieldValue f : fields) {
            switch (f.name()) {
                case "seconds" -> {
                    if (f.value() instanceof Long v) {
                        seconds = v;
                    }
                }
                case "nanos" -> {
                    if (f.value() instanceof Integer v) {
                        nanos = v;
                    }
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

    private static String renderDuration(List<InstanceFieldValue> fields) {
        Long seconds = null;
        Integer nanos = null;
        for (InstanceFieldValue f : fields) {
            switch (f.name()) {
                case "seconds" -> {
                    if (f.value() instanceof Long v) {
                        seconds = v;
                    }
                }
                case "nanos" -> {
                    if (f.value() instanceof Integer v) {
                        nanos = v;
                    }
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

    private static String renderLocalDate(List<InstanceFieldValue> fields) {
        Integer year = null;
        Short month = null;
        Short day = null;
        for (InstanceFieldValue f : fields) {
            switch (f.name()) {
                case "year" -> {
                    if (f.value() instanceof Integer v) {
                        year = v;
                    }
                }
                case "month" -> {
                    if (f.value() instanceof Short v) {
                        month = v;
                    }
                }
                case "day" -> {
                    if (f.value() instanceof Short v) {
                        day = v;
                    }
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

    private static String renderLocalTime(List<InstanceFieldValue> fields) {
        Byte hour = null;
        Byte minute = null;
        Byte second = null;
        Integer nano = null;
        for (InstanceFieldValue f : fields) {
            switch (f.name()) {
                case "hour" -> {
                    if (f.value() instanceof Byte v) {
                        hour = v;
                    }
                }
                case "minute" -> {
                    if (f.value() instanceof Byte v) {
                        minute = v;
                    }
                }
                case "second" -> {
                    if (f.value() instanceof Byte v) {
                        second = v;
                    }
                }
                case "nano" -> {
                    if (f.value() instanceof Integer v) {
                        nano = v;
                    }
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
