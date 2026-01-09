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

import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine;
import org.netbeans.modules.profiler.oql.engine.api.OQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Formats heap Instance objects into human-readable string representations.
 */
public class InstanceValueFormatter {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceValueFormatter.class);
    private static final int MAX_STRING_LENGTH = 200;

    private final OQLEngine engine;

    public InstanceValueFormatter(OQLEngine engine) {
        this.engine = engine;
    }

    /**
     * Format an instance value for display.
     */
    public String format(Instance instance) {
        String className = instance.getJavaClass().getName();

        try {
            return switch (className) {
                case "java.lang.String" -> formatString(instance);
                case "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
                     "java.lang.Double", "java.lang.Float" -> formatPrimitive(instance, "value");
                case "java.lang.Boolean" -> formatBoolean(instance);
                case "java.lang.Character" -> formatCharacter(instance);
                case "java.util.HashMap", "java.util.LinkedHashMap", "java.util.Hashtable",
                     "java.util.concurrent.ConcurrentHashMap" -> formatCollection(instance, "{size=%d}");
                case "java.util.ArrayList", "java.util.LinkedList", "java.util.Vector" -> formatCollection(instance, "[size=%d]");
                case "java.util.HashSet", "java.util.LinkedHashSet", "java.util.TreeSet" -> formatSet(instance);
                case "java.lang.Thread" -> formatWithStringField(instance, "name", "Thread");
                case "java.lang.Class" -> formatWithStringField(instance, "name", "Class");
                case "java.io.File" -> formatWithStringField(instance, "path", "File");
                case "java.lang.StringBuilder", "java.lang.StringBuffer" -> formatStringBuilder(instance);
                case "java.util.Date" -> formatDate(instance);
                default -> formatDefault(instance, className);
            };
        } catch (Exception e) {
            LOG.trace("Failed to format instance value: className={}", className, e);
        }
        return className + "@" + Long.toHexString(instance.getInstanceId());
    }

    private String formatString(Instance instance) {
        // Try direct extraction first
        String value = extractStringValue(instance);
        if (value != null) {
            return "\"" + truncate(value, MAX_STRING_LENGTH) + "\"";
        }

        // Fallback to OQL toString()
        String oqlValue = getValueViaOQL(instance.getInstanceId());
        if (oqlValue != null) {
            return "\"" + truncate(oqlValue, MAX_STRING_LENGTH) + "\"";
        }

        return "String@" + Long.toHexString(instance.getInstanceId());
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

    private String formatPrimitive(Instance instance, String fieldName) {
        Object value = instance.getValueOfField(fieldName);
        return value != null ? value.toString() : "null";
    }

    private String formatBoolean(Instance instance) {
        Object value = instance.getValueOfField("value");
        return value instanceof Boolean b ? b.toString() : "null";
    }

    private String formatCharacter(Instance instance) {
        Object value = instance.getValueOfField("value");
        return value instanceof Character c ? "'" + c + "'" : "null";
    }

    private String formatCollection(Instance instance, String format) {
        Object size = instance.getValueOfField("size");
        return size instanceof Number n ? String.format(format, n.intValue()) : "{...}";
    }

    private String formatSet(Instance instance) {
        Object map = instance.getValueOfField("map");
        if (map instanceof Instance mapInstance) {
            Object size = mapInstance.getValueOfField("size");
            if (size instanceof Number n) {
                return "{size=" + n.intValue() + "}";
            }
        }
        return "{...}";
    }

    private String formatWithStringField(Instance instance, String fieldName, String typeName) {
        Object field = instance.getValueOfField(fieldName);
        if (field instanceof Instance fieldInstance) {
            String value = formatString(fieldInstance);
            return typeName + "[" + value + "]";
        }
        return typeName + "@" + Long.toHexString(instance.getInstanceId());
    }

    private String formatStringBuilder(Instance instance) {
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
            return "\"" + sb + (n.intValue() > MAX_STRING_LENGTH ? "..." : "") + "\"";
        }
        return "StringBuilder@" + Long.toHexString(instance.getInstanceId());
    }

    private String formatDate(Instance instance) {
        Object fastTime = instance.getValueOfField("fastTime");
        return fastTime instanceof Number n
                ? "Date[" + n.longValue() + "]"
                : "Date@" + Long.toHexString(instance.getInstanceId());
    }

    private String formatDefault(Instance instance, String className) {
        for (String fieldName : List.of("name", "value", "id", "key")) {
            Object fieldValue = instance.getValueOfField(fieldName);
            if (fieldValue instanceof Instance fieldInstance
                    && "java.lang.String".equals(fieldInstance.getJavaClass().getName())) {
                return simpleClassName(className) + "[" + formatString(fieldInstance) + "]";
            } else if (fieldValue != null) {
                return simpleClassName(className) + "[" + fieldValue + "]";
            }
        }
        return simpleClassName(className) + "@" + Long.toHexString(instance.getInstanceId());
    }

    private String simpleClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot > 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }

    private String truncate(String str, int maxLen) {
        if (str == null || str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }
}
