/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.profile.viewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.*;
import jdk.jfr.consumer.RecordingFile;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.treetable.EventViewerData;
import pbouda.jeffrey.common.treetable.Tree;
import pbouda.jeffrey.common.treetable.TreeData;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;
import pbouda.jeffrey.profile.summary.ParsingEventSummaryProvider;
import pbouda.jeffrey.profile.summary.event.EventSummary;

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
public class ParsingTreeTableEventViewerProvider implements EventViewerProvider {

    private static final List<String> IGNORED_FIELDS = List.of("stackTrace");
    private final List<Path> recordings;
    private final ActiveSettingsProvider settingsProvider;

    public ParsingTreeTableEventViewerProvider(
            List<Path> recordings,
            ActiveSettingsProvider settingsProvider) {

        this.recordings = recordings;
        this.settingsProvider = settingsProvider;
    }

    @Override
    public JsonNode allEventTypes() {
        Tree tree = new Tree();

        List<EventSummary> eventTypeCount = new ParsingEventSummaryProvider(
                settingsProvider, recordings, ProcessableEvents.all()).get();

        for (EventSummary eventSummary : eventTypeCount) {
            TreeData data = new EventViewerData(
                    eventSummary.categories(),
                    eventSummary.label(),
                    eventSummary.name(),
                    eventSummary.samples(),
                    eventSummary.hasStacktrace());
            tree.add(data);
        }

        return Json.mapper().valueToTree(tree.getRoot().getChildren());
    }

    private static List<EventType> readAllEventTypes(Path recording) {
        try (RecordingFile rec = new RecordingFile(recording)) {
            return rec.readEventTypes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JsonNode events(Type eventType) {
        return JdkRecordingIterators.automaticAndCollect(
                recordings,
                () -> new ListEventsProcessor(eventType, IGNORED_FIELDS),
                new ArrayNodeCollector());
    }

    @Override
    public JsonNode eventColumns(Type eventType) {
        Optional<List<ValueDescriptor>> fieldsOpt = recordings.stream()
                .flatMap(p -> readAllEventTypes(p).stream())
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