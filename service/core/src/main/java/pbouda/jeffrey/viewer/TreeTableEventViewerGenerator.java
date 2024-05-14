package pbouda.jeffrey.viewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.*;
import jdk.jfr.consumer.RecordingFile;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.jfr.event.AllEventsProvider;
import pbouda.jeffrey.jfr.event.EventSummary;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * <a href="https://primevue.org/treetable/#template">PrimeVue TreeTable</a>
 * <pre>
 *  [{
 *   "key": "0",
 *   "data": {
 *     "label": "Documents",
 *     "data": "Documents Folder",
 *     "icon": "pi pi-fw pi-inbox"
 *   },
 *   "children": [
 *     {
 *       "key": "0-0",
 *       "data": {
 *         "label": "Work",
 *         "data": "Work Folder",
 *         "icon": "pi pi-fw pi-cog"
 *       },
 *       "children": [
 *         { "key": "0-0-0", "label": "Expenses.doc", "icon": "pi pi-fw pi-file", "data": "Expenses Document" },
 *         { "key": "0-0-1", "label": "Resume.doc", "icon": "pi pi-fw pi-file", "data": "Resume Document" }
 *       ]
 *     },
 *     {
 *       "key": "0-1",
 *       "data": {
 *         "label": "Home",
 *         "data": "Home Folder",
 *         "icon": "pi pi-fw pi-home"
 *       },
 *       "children": [{ "key": "0-1-0", "label": "Invoices.txt", "icon": "pi pi-fw pi-file", "data": "Invoices for this month" }]
 *     }
 *   ]
 * }]""");
 * </pre>
 *
 * <a href="https://primevue.org/datatable/#dynamic_columns">DataTable Dynamic Columns</a>
 * <pre>
 * const columns = [
 *     { field: 'code', header: 'Code' },
 *     { field: 'name', header: 'Name' },
 *     { field: 'category', header: 'Category' },
 *     { field: 'quantity', header: 'Quantity' }
 * ];
 * </pre>
 */
public class TreeTableEventViewerGenerator implements EventViewerGenerator {

    private static final List<String> IGNORED_FIELDS = List.of("stackTrace");

    @Override
    public JsonNode allEventTypes(Path recording) {
        Tree tree = new Tree();
        List<EventSummary> eventTypeCount = new AllEventsProvider(recording).get();
        for (EventSummary eventSummary : eventTypeCount) {
            EventType eventType = eventSummary.eventType();
            tree.add(
                    eventType.getCategoryNames(),
                    eventType.getLabel(),
                    eventType.getName(),
                    eventSummary.samples(),
                    containsStackTrace(eventType)
            );
        }

        return Json.mapper().valueToTree(tree.getRoot().getChildren());
    }

    private static boolean containsStackTrace(EventType event) {
        return event.getField("stackTrace") != null;
    }

    private static List<EventType> readAllEventTypes(Path recording) {
        try (RecordingFile rec = new RecordingFile(recording)) {
            return rec.readEventTypes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JsonNode events(Path path, Type eventType) {
        return new RecordingFileIterator<>(path, new ListEventsProcessor(eventType, IGNORED_FIELDS))
                .collect();
    }

    @Override
    public JsonNode eventColumns(Path path, Type eventType) {
        Optional<List<ValueDescriptor>> fieldsOpt = readAllEventTypes(path).stream()
                .filter(e -> e.getName().equals(eventType.code()))
                .map(EventType::getFields)
                .findFirst();

        ArrayNode result = Json.createArray();
        if (fieldsOpt.isPresent()) {
            for (ValueDescriptor desc : fieldsOpt.get()) {
                if (!IGNORED_FIELDS.contains(desc.getName())) {
                    ObjectNode type = Json.createObject()
                            .put("field", desc.getName())
                            .put("header", desc.getLabel())
                            .put("type", getContentType(desc))
                            .put("description", desc.getDescription());
                    result.add(type);
                }
            }
        }

        return result;
    }

    public String getContentType(ValueDescriptor desc) {
        boolean lowPriority = true;
        String resolvedType = null;

        for (AnnotationElement anno : desc.getAnnotationElements()) {
            for (AnnotationElement meta : anno.getAnnotationElements()) {
                if (meta.getTypeName().equals(ContentType.class.getName())) {
                    String contentType = anno.getTypeName();
                    if (contentType.equals(Unsigned.class.getTypeName()) && lowPriority) {
                        resolvedType = contentType;
                    } else if (lowPriority) {
                        lowPriority = false;
                        resolvedType = contentType;
                    }
                }
            }
        }

        return resolvedType;
    }
}
