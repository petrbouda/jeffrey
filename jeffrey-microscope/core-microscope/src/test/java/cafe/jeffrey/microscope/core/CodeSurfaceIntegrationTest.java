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

package cafe.jeffrey.microscope.core;

import cafe.jeffrey.flamegraph.code.MethodKey;
import cafe.jeffrey.flamegraph.code.MethodRef;
import cafe.jeffrey.provider.profile.api.CallSiteRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.test.recording.TestappRecording;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.HashMap;
import java.util.Map;

import static cafe.jeffrey.test.recording.TestappRecording.DIRECT;
import static cafe.jeffrey.test.recording.TestappRecording.HTTP_SERVER_EXCHANGE;
import static cafe.jeffrey.test.recording.TestappRecording.JDBC_QUERY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The runtime surface of the testapp, tied to its code: its JDBC statements carry their caller's stack,
 * so each names the lines of the service that issue it, and its requests - recorded without stacks -
 * are placed in code through the samples taken inside their spans. Figures are held to the statements
 * {@link TestappRecording} pins.
 */
class CodeSurfaceIntegrationTest extends TestappVolumeMicroscope {

    private static final String OWN_PACKAGE = "jeffrey.testapp.";
    private static final String COUNT_OPERATION = "GET /persons/count";
    private static final String COUNT_HANDLER = "jeffrey.testapp.server.PersonController#personCount";
    private static final String QUERY_STATEMENTS = "SELECT json_extract_string(fields, '$.name'), COUNT(*) FROM events "
            + "WHERE event_type = 'jeffrey.JdbcQuery' GROUP BY 1";

    private static ArrayNode own() {
        return Json.createArray().add(OWN_PACKAGE);
    }

    @Nested
    class CallSites {

        @Test
        void everyQueryIsChargedToTheServicesOwnCodeAndCountsAsTheRecordingDoes() {
            JsonNode answer = answer("code_callSites", DIRECT, Json.createObject()
                    .put("eventType", JDBC_QUERY)
                    .put("limit", 50)
                    .set("framePrefixes", own()));

            assertEquals("OK", answer.path("status").asString(), answer::toString);
            assertEquals("name", answer.path("groupBy").asString());
            assertEquals(DIRECT.traces().queries(), answer.path("totalEvents").asInt());
            assertEquals(0, answer.path("unattributedEvents").asInt());
            assertEquals(0, answer.path("omittedSites").asInt());

            Map<String, Integer> byStatement = new HashMap<>();
            for (JsonNode site : answer.path("sites")) {
                assertTrue(site.path("className").asString().startsWith(OWN_PACKAGE), site.toString());
                assertTrue(site.path("ownPath").size() > 1, site.toString());
                MethodKey frame = new MethodKey(site.path("className").asString(), site.path("methodName").asString());
                assertTrue(MethodRef.parse(site.path("reference").asString()).matcher().matches(frame), site.toString());
                byStatement.merge(site.path("groupValue").asString(), site.path("count").asInt(), Integer::sum);
            }
            Map<String, Integer> recorded = new HashMap<>();
            query(DIRECT, QUERY_STATEMENTS).forEach(row -> recorded.put(row.get(0), Integer.parseInt(row.get(1))));
            assertEquals(recorded, byStatement);
            byStatement.forEach((statement, count) ->
                    assertEquals(DIRECT.traces().statementsByName().get(statement), count, statement));
        }

        @Test
        void aLongGroupValueIsCutInTheReadAndKeepsItsLengthAndItsEvents() {
            JsonNode answer = answer("code_callSites", DIRECT, Json.createObject()
                    .put("eventType", JDBC_QUERY)
                    .put("groupBy", "sql")
                    .put("limit", 50)
                    .set("framePrefixes", own()));

            assertEquals("OK", answer.path("status").asString(), answer::toString);
            long counted = 0;
            for (JsonNode site : answer.path("sites")) {
                String value = site.path("groupValue").asString();
                assertTrue(value.length() <= CallSiteRecord.MAX_GROUP_VALUE_CHARS, site.toString());
                assertTrue(site.path("groupValueChars").asInt() >= value.length(), site.toString());
                counted += site.path("count").asLong();
            }
            assertEquals(0, answer.path("omittedSites").asInt(), answer::toString);
            assertEquals(answer.path("totalEvents").asLong(), counted, "every statement counted once, none merged away");
        }

        @Test
        void aStatementInsideARequestIsCountedPerTrace() {
            JsonNode answer = answer("code_callSites", DIRECT, Json.createObject()
                    .put("eventType", JDBC_QUERY)
                    .put("filterField", "name")
                    .put("filterValue", "find_by_id")
                    .set("framePrefixes", own()));

            long traces = 0;
            for (JsonNode site : answer.path("sites")) {
                assertEquals("find_by_id", site.path("groupValue").asString());
                assertEquals(1, site.path("perTraceMax").asInt(), site.toString());
                traces += site.path("traces").asLong();
            }
            assertEquals(DIRECT.traces().statementsByName().get("find_by_id").longValue(), traces);
        }

        @Test
        void anExchangeRecordedWithoutStacksSaysSo() {
            JsonNode answer = answer("code_callSites", DIRECT, Json.createObject()
                    .put("eventType", HTTP_SERVER_EXCHANGE)
                    .set("framePrefixes", own()));

            assertEquals("NO_STACKS", answer.path("status").asString(), answer::toString);
            assertTrue(answer.path("sites").isEmpty());
        }
    }

    @Nested
    class RuntimeSurface {

        @Test
        void placesTheRequestsInTheServicesCodeAndTheStatementsAtTheirLines() {
            JsonNode surface = answer("code_runtimeSurface", DIRECT, Json.createObject().set("framePrefixes", own()));

            JsonNode operations = surface.path("operations");
            assertEquals("READ", operations.path("state").asString(), operations::toString);
            boolean placed = false;
            for (JsonNode operation : operations.path("operations")) {
                for (JsonNode entry : operation.path("entryPoints")) {
                    assertTrue(entry.path("frame").asString().startsWith(OWN_PACKAGE), entry.toString());
                    placed = true;
                }
            }
            assertTrue(placed, operations::toString);
            JsonNode count = null;
            for (JsonNode operation : operations.path("operations")) {
                if (COUNT_OPERATION.equals(operation.path("name").asString())) {
                    count = operation;
                }
            }
            assertTrue(count != null, operations::toString);
            assertEquals("EVENT_STACK", count.path("entryBasis").asString(), count::toString);
            assertEquals(COUNT_HANDLER, count.path("entryPoints").get(0).path("frame").asString(), count::toString);

            Map<String, Integer> statements = new HashMap<>();
            for (JsonNode statement : surface.path("statements").path("statements")) {
                statements.put(statement.path("statement").asString(), statement.path("count").asInt());
                assertTrue(statement.path("callSites").size() > 0, statement.toString());
            }
            assertEquals(DIRECT.traces().statementsByName().get("count_query"), statements.get("count_query"));

            boolean mode = false;
            for (JsonNode key : surface.path("attributeKeys")) {
                mode |= TestappRecording.MODE.equals(key.path("key").asString());
            }
            assertTrue(mode, surface.path("attributeKeys")::toString);
        }
    }
}
