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

package pbouda.jeffrey.profile.heapdump.analyzer;

import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.ObjectArrayInstance;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine;
import org.netbeans.modules.profiler.oql.engine.api.OQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Formats heap Instance objects into structured key/value pairs
 * representing the most interesting properties of the object.
 */
public class InstanceValueFormatter {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceValueFormatter.class);
    private static final int MAX_STRING_LENGTH = 200;

    private final OQLEngine engine;

    public InstanceValueFormatter(OQLEngine engine) {
        this.engine = engine;
    }

    /**
     * Format an instance into structured key/value pairs.
     */
    public Map<String, String> format(Instance instance) {
        if (instance instanceof ObjectArrayInstance arr) {
            return Map.of("length", String.valueOf(arr.getLength()));
        }
        if (instance instanceof PrimitiveArrayInstance arr) {
            return Map.of("length", String.valueOf(arr.getLength()));
        }

        String className = instance.getJavaClass().getName();

        try {
            return switch (className) {
                case "java.lang.String" -> formatString(instance);
                case "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
                     "java.lang.Double", "java.lang.Float" -> formatPrimitive(instance);
                case "java.lang.Boolean" -> formatBoolean(instance);
                case "java.lang.Character" -> formatCharacter(instance);
                case "java.util.HashMap", "java.util.LinkedHashMap", "java.util.Hashtable",
                     "java.util.concurrent.ConcurrentHashMap" -> formatCollection(instance);
                case "java.util.ArrayList", "java.util.LinkedList", "java.util.Vector" -> formatCollection(instance);
                case "java.util.HashSet", "java.util.LinkedHashSet", "java.util.TreeSet" -> formatSet(instance);
                case "java.lang.Thread" -> formatWithStringField(instance, "name");
                case "java.lang.Class" -> formatWithStringField(instance, "name");
                case "java.io.File" -> formatWithStringField(instance, "path");
                case "java.lang.StringBuilder", "java.lang.StringBuffer" -> formatStringBuilder(instance);
                case "java.util.Date" -> formatDate(instance);
                default -> formatDefault(instance);
            };
        } catch (Exception e) {
            LOG.trace("Failed to format instance value: className={}", className, e);
        }
        return Map.of();
    }

    /**
     * Format a String instance and return the raw string value (without surrounding quotes).
     * This is useful for extracting plain text, e.g. for thread names.
     */
    public String formatStringValue(Instance instance) {
        String value = extractStringValue(instance);
        if (value != null) {
            return truncate(value, MAX_STRING_LENGTH);
        }
        String oqlValue = getValueViaOQL(instance.getInstanceId());
        if (oqlValue != null) {
            return truncate(oqlValue, MAX_STRING_LENGTH);
        }
        return null;
    }

    private Map<String, String> formatString(Instance instance) {
        String value = extractStringValue(instance);
        if (value != null) {
            return Map.of("value", "\"" + truncate(value, MAX_STRING_LENGTH) + "\"");
        }

        String oqlValue = getValueViaOQL(instance.getInstanceId());
        if (oqlValue != null) {
            return Map.of("value", "\"" + truncate(oqlValue, MAX_STRING_LENGTH) + "\"");
        }

        return Map.of();
    }

    private String extractStringValue(Instance stringInstance) {
        Object valueField = stringInstance.getValueOfField("value");

        if (!(valueField instanceof List<?> valueArray) || valueArray.isEmpty()) {
            return null;
        }

        // JDK 9+: byte[] with coder, JDK 8: char[]
        Object coderField = stringInstance.getValueOfField("coder");
        boolean isLatin1 = coderField == null || (coderField instanceof Number n && n.intValue() == 0);

        return isLatin1 ? decodeLatin1(valueArray) : decodeUtf16(valueArray);
    }

    private String decodeLatin1(List<?> valueArray) {
        StringBuilder sb = new StringBuilder();
        int len = Math.min(valueArray.size(), MAX_STRING_LENGTH);
        for (int i = 0; i < len; i++) {
            Object val = valueArray.get(i);
            if (val instanceof Character c) {
                sb.append(c);
            } else if (val instanceof Number n) {
                sb.append((char) (n.intValue() & 0xFF));
            }
        }
        return sb.toString();
    }

    private String decodeUtf16(List<?> valueArray) {
        StringBuilder sb = new StringBuilder();
        int byteLen = Math.min(valueArray.size(), MAX_STRING_LENGTH * 2);
        for (int i = 0; i < byteLen - 1; i += 2) {
            Object b1 = valueArray.get(i);
            Object b2 = valueArray.get(i + 1);
            if (b1 instanceof Number n1 && b2 instanceof Number n2) {
                int ch = ((n1.intValue() & 0xFF)) | ((n2.intValue() & 0xFF) << 8);
                sb.append((char) ch);
            }
        }
        return sb.toString();
    }

    private String getValueViaOQL(long objectId) {
        if (engine == null) return null;

        StringBuilder result = new StringBuilder();
        try {
            engine.executeQuery("select heap.findObject(" + objectId + ").toString()", obj -> {
                if (obj != null) result.append(obj);
                return true;
            });
            return result.isEmpty() ? null : result.toString();
        } catch (OQLException e) {
            LOG.trace("Failed to get string via OQL: objectId={}", objectId);
            return null;
        }
    }

    private Map<String, String> formatPrimitive(Instance instance) {
        Object value = instance.getValueOfField("value");
        return value != null ? Map.of("value", value.toString()) : Map.of();
    }

    private Map<String, String> formatBoolean(Instance instance) {
        Object value = instance.getValueOfField("value");
        return value instanceof Boolean b ? Map.of("value", b.toString()) : Map.of();
    }

    private Map<String, String> formatCharacter(Instance instance) {
        Object value = instance.getValueOfField("value");
        return value instanceof Character c ? Map.of("value", "'" + c + "'") : Map.of();
    }

    private Map<String, String> formatCollection(Instance instance) {
        Object size = instance.getValueOfField("size");
        return size instanceof Number n ? Map.of("size", String.valueOf(n.intValue())) : Map.of();
    }

    private Map<String, String> formatSet(Instance instance) {
        Object map = instance.getValueOfField("map");
        if (map instanceof Instance mapInstance) {
            Object size = mapInstance.getValueOfField("size");
            if (size instanceof Number n) {
                return Map.of("size", String.valueOf(n.intValue()));
            }
        }
        return Map.of();
    }

    private Map<String, String> formatWithStringField(Instance instance, String fieldName) {
        Object field = instance.getValueOfField(fieldName);
        if (field instanceof Instance fieldInstance) {
            String value = formatStringValue(fieldInstance);
            if (value != null) {
                return Map.of(fieldName, "\"" + value + "\"");
            }
        }
        return Map.of();
    }

    private Map<String, String> formatStringBuilder(Instance instance) {
        Object value = instance.getValueOfField("value");
        Object count = instance.getValueOfField("count");
        if (value instanceof List<?> chars && count instanceof Number n) {
            StringBuilder sb = new StringBuilder();
            int len = Math.min(n.intValue(), Math.min(chars.size(), MAX_STRING_LENGTH));
            for (int i = 0; i < len; i++) {
                if (chars.get(i) instanceof Number num) {
                    sb.append((char) num.intValue());
                }
            }
            return Map.of("value", "\"" + sb + "\"");
        }
        return Map.of();
    }

    private Map<String, String> formatDate(Instance instance) {
        Object fastTime = instance.getValueOfField("fastTime");
        return fastTime instanceof Number n
                ? Map.of("timestamp", String.valueOf(n.longValue()))
                : Map.of();
    }

    private Map<String, String> formatDefault(Instance instance) {
        for (String fieldName : List.of("name", "value", "id", "key")) {
            Object fieldValue = instance.getValueOfField(fieldName);
            if (fieldValue instanceof Instance fieldInstance) {
                String fieldClassName = fieldInstance.getJavaClass().getName();
                if ("java.lang.String".equals(fieldClassName)) {
                    String strValue = formatStringValue(fieldInstance);
                    if (strValue != null) {
                        return Map.of(fieldName, "\"" + strValue + "\"");
                    }
                }
            } else if (fieldValue != null) {
                return Map.of(fieldName, fieldValue.toString());
            }
        }
        return Map.of();
    }

    /**
     * Format an instance as a single display string for contexts that need a String value
     * (e.g. instance detail panels, OQL results, instance tree nodes).
     * Produces: "key1=v1, key2=v2" or empty string if no params.
     */
    public String formatAsString(Instance instance) {
        Map<String, String> params = format(instance);
        if (params.isEmpty()) {
            return "@" + Long.toHexString(instance.getInstanceId());
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    private String truncate(String str, int maxLen) {
        if (str == null || str.length() <= maxLen) return str;
        return str.substring(0, maxLen);
    }
}
