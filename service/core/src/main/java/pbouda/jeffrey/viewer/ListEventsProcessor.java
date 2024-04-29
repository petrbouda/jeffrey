package pbouda.jeffrey.viewer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedThread;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.common.RecordedClassMapper;
import pbouda.jeffrey.jfrparser.jdk.SingleEventProcessor;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

public class ListEventsProcessor extends SingleEventProcessor implements Supplier<ArrayNode> {

    private final ArrayNode result = Json.createArray();
    private final List<String> ignoredFields;

    public ListEventsProcessor(EventType eventType, List<String> ignoredFields) {
        super(eventType);
        this.ignoredFields = ignoredFields;
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        ObjectNode node = Json.createObject();
        for (ValueDescriptor field : event.getFields()) {
            if (!ignoredFields.contains(field.getName())) {
                if ("long".equals(field.getTypeName()) && "jdk.jfr.Timestamp".equals(field.getContentType())) {
                    Instant instant = event.getInstant(field.getName());
                    node.put(field.getName(), instant.toEpochMilli());
                } else if ("jdk.jfr.Percentage".equals(field.getContentType())) {
                    float value = event.getFloat(field.getName());
                    node.put(field.getName(), value);
                } else if ("java.lang.Thread".equals(field.getTypeName())) {
                    RecordedThread value = event.getThread(field.getName());
                    node.put(field.getName(), value.getJavaName());
                } else if ("java.lang.Class".equals(field.getTypeName())) {
                    RecordedClass clazz = event.getClass(field.getName());
                    node.put(field.getName(), RecordedClassMapper.map(clazz));
                } else if ("jdk.types.Method".equals(field.getTypeName())) {
                    RecordedMethod method = event.getValue(field.getName());
                    node.put(field.getName(), method.getType().getName() + "#" + method.getName());
                } else {
                    String value = safeToString(event.getValue(field.getName()));
                    node.put(field.getName(), value);
                }
            }
        }
        result.add(node);
        return Result.CONTINUE;
    }

    private static String safeToString(Object val) {
        return val == null ? null : val.toString();
    }

    @Override
    public ArrayNode get() {
        return result;
    }
}
