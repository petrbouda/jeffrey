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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The profile database as a query sees it, read from the real schema the parser writes: every table
 * and every view -- above all the {@code events} view, which is what every reader queries and what
 * the table listing used to leave out.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class ProfileSchemaReaderTest {

    private static final String PROFILE_ID = "p-1";
    private static final String EVENTS_VIEW = "events";
    private static final String EVENT_TYPES_LINK = "http://localhost/profiles/p-1/event-types";

    /** Every relation {@code V001__init.sql} creates, the one view included. */
    private static final Set<String> PROFILE_RELATIONS = Set.of(
            "cache", "event_types", "frames", "stacktraces", "events_raw", "field_texts", "events",
            "threads", "profile_info", "pipeline_runs", "trace_spans", "traces", "trace_notifications",
            "trace_notification_messages", "trace_exceptions", "trace_span_payloads",
            "trace_span_attributes", "trace_notification_attributes", "trace_attribute_values",
            "trace_attribute_keys", "trace_attribute_key_event_types");

    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void resetRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    class ListTables {

        /**
         * DuckDB's driver reports a view as {@code VIEW}, so asking for tables alone left out the one
         * relation every query is written against.
         */
        @Test
        void namesTheEventsView(DataSource dataSource) {
            String listing = new DuckDbMcpTools(dataSource, "p-1", AdvertisedFamiliesFixture.EVERY_FAMILY)
                    .listTables().text();

            assertTrue(listing.contains("{\"name\":\"events\",\"view\":true}"), listing);
            assertTrue(listing.contains("{\"name\":\"events_raw\",\"view\":false}"), listing);
            assertFalse(listing.contains("flyway_"), listing);
        }
    }

    @Nested
    class Schema {

        @Test
        void listsEveryTableAndViewOfTheProfileDatabase(DataSource dataSource) {
            JsonNode schema = read(dataSource);

            Set<String> names = new TreeSet<>();
            for (JsonNode relation : schema.get("relations")) {
                names.add(relation.get("name").asString());
            }
            assertEquals(new TreeSet<>(PROFILE_RELATIONS), names);
            assertTrue(relation(schema, EVENTS_VIEW).get("view").asBoolean());
            assertFalse(relation(schema, "events_raw").get("view").asBoolean());
        }

        @Test
        void describesTheEventsViewWithItsColumnsAndTheFieldsNote(DataSource dataSource) {
            JsonNode events = relation(read(dataSource), EVENTS_VIEW);

            List<String> columns = new ArrayList<>();
            for (JsonNode column : events.get("columns")) {
                columns.add(column.get("name").asString());
            }
            assertEquals(List.of("event_type", "start_timestamp", "start_timestamp_from_beginning", "duration",
                    "samples", "weight", "weight_entity", "stacktrace_hash", "thread_hash", "fields"), columns);
            assertEquals("JSON", events.get("columns").get(9).get("type").asString());
            assertTrue(events.get("note").asString().contains("fields->>'key'"), events.toString());
            assertTrue(relation(read(dataSource), "threads").get("note").isNull());
        }

        @Test
        void countsTheEventsOfEveryEventType(DataSource dataSource) throws SQLException {
            seedEvents(dataSource);

            JsonNode eventTypes = read(dataSource).get("eventTypes");

            assertEquals(2, eventTypes.size());
            assertEquals("jdk.ObjectAllocationSample", eventTypes.get(0).get("name").asString());
            assertEquals(2, eventTypes.get(0).get("count").asLong());
            assertEquals(3, eventTypes.get(0).get("samples").asLong());
            assertEquals("Allocation", eventTypes.get(0).get("label").asString());
            assertEquals("jdk.GCPhasePause", eventTypes.get(1).get("name").asString());
            assertEquals(0, eventTypes.get(1).get("count").asLong());
            assertTrue(eventTypes.get(1).get("description").isNull());
        }

        @Test
        void linksTheEventTypesPage(DataSource dataSource) {
            JsonNode schema = read(dataSource);

            assertEquals(PROFILE_ID, schema.get("profileId").asString());
            assertEquals(EVENT_TYPES_LINK, schema.get("uiLink").asString());
        }

        @Test
        void linksAPageTheFrontendServes() {
            assertTrue(ProfileRouteManifest.routes().contains(ProfileSchemaReader.EVENT_TYPES_VIEW.path()));
        }

        @Test
        void conformsToTheGeneratedSchema(DataSource dataSource) throws SQLException {
            seedEvents(dataSource);

            McpSchemaConformance.assertConforms(read(dataSource), McpSchemaGenerator.schemaOf(ProfileSchema.class));
        }
    }

    private static JsonNode read(DataSource dataSource) {
        return Json.readTree(Json.toString(new ProfileSchemaReader(dataSource).read(PROFILE_ID)));
    }

    private static JsonNode relation(JsonNode schema, String name) {
        for (JsonNode relation : schema.get("relations")) {
            if (name.equals(relation.get("name").asString())) {
                return relation;
            }
        }
        throw new AssertionError("no relation " + name + " in " + schema);
    }

    private static void seedEvents(DataSource dataSource) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO event_types (name, label, description, source, has_stacktrace) VALUES "
                    + "('jdk.ObjectAllocationSample', 'Allocation', 'A sampled allocation', 'JDK', true), "
                    + "('jdk.GCPhasePause', 'GC Pause', NULL, 'JDK', false)");
            stmt.execute("INSERT INTO events_raw (event_type, start_timestamp, samples, fields) VALUES "
                    + "('jdk.ObjectAllocationSample', TIMESTAMPTZ '2026-01-01 00:00:00+00', 1, '{}'), "
                    + "('jdk.ObjectAllocationSample', TIMESTAMPTZ '2026-01-01 00:00:01+00', 2, '{}')");
        }
    }
}
