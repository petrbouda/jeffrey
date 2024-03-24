package pbouda.jeffrey.viewer;

import com.fasterxml.jackson.databind.JsonNode;
import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import pbouda.jeffrey.Json;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
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
 */
public class TreeTableEventViewerGenerator implements EventViewerGenerator {

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
}
