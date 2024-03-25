package pbouda.jeffrey.viewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.EventType;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import pbouda.jeffrey.Json;
import pbouda.jeffrey.generator.timeseries.TimeseriesEventProcessor;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 *
 *
 */
public class TreeTableEventViewerGenerator implements EventViewerGenerator {

    private static final List<String> IGNORED_FIELDS = List.of("stackTrace");

    @Override
    public JsonNode allEventTypes(Path recording) {
        Tree tree = new Tree();
        Map<String, Long> eventTypeCount = eventCounts(recording);
        for (EventType eventType : readAllEventTypes(recording)) {
            tree.add(eventType.getCategoryNames(), eventType.getLabel(), eventType.getName(), eventTypeCount.getOrDefault(eventType.getLabel(), 0L));
        }

        return Json.mapper().valueToTree(tree.getRoot().getChildren());
    }

    private static Map<String, Long> eventCounts(Path recording) {
        Map<String, Long> counts = new HashMap<>();
        try (RecordingFile rec = new RecordingFile(recording)) {
            while(rec.hasMoreEvents()) {
                RecordedEvent event = rec.readEvent();
                counts.merge(event.getEventType().getLabel(), 1L, Long::sum);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return counts;
    }

    private static List<EventType> readAllEventTypes(Path recording) {
        try (RecordingFile rec = new RecordingFile(recording)) {
            return rec.readEventTypes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JsonNode events(Path path, pbouda.jeffrey.common.EventType eventType) {
        return new RecordingFileIterator<>(path, new ListEventsProcessor(eventType, IGNORED_FIELDS))
                .collect();
    }

    @Override
    public JsonNode eventColumns(Path path, pbouda.jeffrey.common.EventType eventType) {
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
                            .put("type", desc.getContentType())
                            .put("description", desc.getDescription());
                    result.add(type);
                }
            }
        }

        return result;
    }
}
