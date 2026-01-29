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
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.ObjectFieldValue;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine;
import org.netbeans.modules.profiler.oql.engine.api.OQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.InstanceDetail;
import pbouda.jeffrey.profile.heapdump.model.InstanceField;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes individual heap instances, extracting their field values and details.
 */
public class InstanceDetailAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceDetailAnalyzer.class);

    /**
     * Get detailed information about an instance including all its fields.
     *
     * @param heap                the loaded heap dump
     * @param objectId            the object ID to analyze
     * @param includeRetainedSize whether to calculate retained size (expensive operation)
     * @return instance details or null if not found
     */
    @SuppressWarnings("unchecked")
    public InstanceDetail analyze(Heap heap, long objectId, boolean includeRetainedSize) {
        Instance instance = (Instance) heap.getInstanceByID(objectId);
        if (instance == null) {
            LOG.debug("Instance not found: objectId={}", objectId);
            return null;
        }

        OQLEngine engine = new OQLEngine(heap);
        InstanceValueFormatter formatter = new InstanceValueFormatter(engine);

        String className = instance.getJavaClass().getName();
        String value = formatter.formatAsString(instance);
        String stringValue = extractStringValue(engine, instance, className);

        List<InstanceField> fields = extractFields(instance, formatter);
        List<InstanceField> staticFields = extractStaticFields(instance, formatter);

        Long retainedSize = null;
        if (includeRetainedSize) {
            long retained = instance.getRetainedSize();
            retainedSize = retained > 0 ? retained : null;
        }

        return new InstanceDetail(
                objectId,
                className,
                value,
                stringValue,
                instance.getSize(),
                retainedSize,
                fields,
                staticFields
        );
    }

    @SuppressWarnings("unchecked")
    private List<InstanceField> extractFields(Instance instance, InstanceValueFormatter formatter) {
        List<InstanceField> fields = new ArrayList<>();
        List<FieldValue> fieldValues = (List<FieldValue>) instance.getFieldValues();

        for (FieldValue fieldValue : fieldValues) {
            fields.add(toInstanceField(fieldValue, formatter));
        }

        return fields;
    }

    @SuppressWarnings("unchecked")
    private List<InstanceField> extractStaticFields(Instance instance, InstanceValueFormatter formatter) {
        List<InstanceField> staticFields = new ArrayList<>();
        List<FieldValue> fieldValues = (List<FieldValue>) instance.getJavaClass().getStaticFieldValues();

        for (FieldValue fieldValue : fieldValues) {
            staticFields.add(toInstanceField(fieldValue, formatter));
        }

        return staticFields;
    }

    private InstanceField toInstanceField(FieldValue fieldValue, InstanceValueFormatter formatter) {
        String name = fieldValue.getField().getName();
        String type = fieldValue.getField().getType().getName();

        if (fieldValue instanceof ObjectFieldValue objectFieldValue) {
            Instance referencedInstance = objectFieldValue.getInstance();
            if (referencedInstance == null) {
                return InstanceField.nullReference(name, type);
            }

            String value = formatter.formatAsString(referencedInstance);
            return InstanceField.reference(name, type, value, referencedInstance.getInstanceId());
        } else {
            // Primitive value
            String value = fieldValue.getValue();
            return InstanceField.primitive(name, type, value);
        }
    }

    /**
     * Extract raw string value for String instances or decode byte[] arrays.
     */
    @SuppressWarnings("unchecked")
    private String extractStringValue(OQLEngine engine, Instance instance, String className) {
        try {
            if ("java.lang.String".equals(className)) {
                // Try direct extraction first
                String value = extractStringFromInstance(instance);
                if (value != null) {
                    return value;
                }
                // Fallback to OQL - this reliably gets the full string value
                return getStringValueViaOQL(engine, instance.getInstanceId());
            } else if (className.equals("byte[]") || className.equals("[B")) {
                return extractStringFromByteArray(instance);
            } else if (className.equals("char[]") || className.equals("[C")) {
                return extractStringFromCharArray(instance);
            }
        } catch (Exception e) {
            LOG.trace("Failed to extract string value: className={}", className, e);
        }
        return null;
    }

    /**
     * Get string value via OQL toString() - works reliably for all String instances.
     */
    private String getStringValueViaOQL(OQLEngine engine, long objectId) {
        StringBuilder result = new StringBuilder();
        try {
            engine.executeQuery("select heap.findObject(" + objectId + ").toString()", obj -> {
                if (obj != null) {
                    result.append(obj);
                }
                return true;
            });
            return result.isEmpty() ? null : result.toString();
        } catch (OQLException e) {
            LOG.trace("Failed to get string via OQL: objectId={}", objectId);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractStringFromInstance(Instance stringInstance) {
        Object valueField = stringInstance.getValueOfField("value");
        if (!(valueField instanceof List<?> valueArray) || valueArray.isEmpty()) {
            return null;
        }

        // JDK 9+: byte[] with coder, JDK 8: char[]
        Object coderField = stringInstance.getValueOfField("coder");
        boolean isLatin1 = coderField == null || (coderField instanceof Number n && n.intValue() == 0);

        return isLatin1 ? decodeLatin1(valueArray) : decodeUtf16(valueArray);
    }

    @SuppressWarnings("unchecked")
    private String extractStringFromByteArray(Instance instance) {
        if (!(instance instanceof PrimitiveArrayInstance arrayInstance)) {
            return null;
        }

        List<String> values = (List<String>) arrayInstance.getValues();
        if (values == null || values.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (String val : values) {
            try {
                int byteVal = Integer.parseInt(val);
                if (byteVal >= 32 && byteVal < 127) {
                    sb.append((char) byteVal);
                } else if (byteVal == 10) {
                    sb.append("\\n");
                } else if (byteVal == 13) {
                    sb.append("\\r");
                } else if (byteVal == 9) {
                    sb.append("\\t");
                } else {
                    sb.append(".");
                }
            } catch (NumberFormatException e) {
                sb.append("?");
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractStringFromCharArray(Instance instance) {
        if (!(instance instanceof PrimitiveArrayInstance arrayInstance)) {
            return null;
        }

        List<String> values = (List<String>) arrayInstance.getValues();
        if (values == null || values.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (String val : values) {
            if (val != null && val.length() == 1) {
                sb.append(val.charAt(0));
            }
        }
        return sb.toString();
    }

    private String decodeLatin1(List<?> valueArray) {
        StringBuilder sb = new StringBuilder();
        for (Object val : valueArray) {
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
        for (int i = 0; i < valueArray.size() - 1; i += 2) {
            Object b1 = valueArray.get(i);
            Object b2 = valueArray.get(i + 1);
            if (b1 instanceof Number n1 && b2 instanceof Number n2) {
                int ch = ((n1.intValue() & 0xFF)) | ((n2.intValue() & 0xFF) << 8);
                sb.append((char) ch);
            }
        }
        return sb.toString();
    }
}
