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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.microscope.mcp.protocol.McpInputResponse;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadWindowQuestionTest {

    private static final long GIB = 1024L * 1024 * 1024;
    private static final Instant NOW = Instant.parse("2026-03-01T12:30:00Z");
    private static final Instant CREATED = Instant.parse("2026-03-01T09:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-03-01T12:00:00Z");

    private static final DownloadWindowQuestion QUESTION = new DownloadWindowQuestion(Duration.ofHours(1), GIB);

    private static final Set<String> PRIMITIVE_TYPES = Set.of("string", "integer", "number", "boolean");
    private static final Set<String> CHOICE_KEYS = Set.of("const", "title");

    private static RecordingSession session(Instant createdAt, Instant finishedAt, long bytes) {
        RepositoryFile chunk = new RepositoryFile("c0", "profile-0.jfr", createdAt, bytes, true, null);
        return new RecordingSession("session-1", "checkout-api", "inst-1", createdAt, finishedAt,
                finishedAt == null ? RecordingStatus.ACTIVE : RecordingStatus.FINISHED, null, List.of(chunk), false);
    }

    /** Three hours and two gigabytes: over both thresholds. */
    private static RecordingSession large() {
        return session(CREATED, FINISHED, 2 * GIB);
    }

    private static WindowSubject subject(RecordingSession session) {
        return new WindowSubject(session, "production", "checkout", NOW);
    }

    /** Twelve compressed chunks of a quarter of an hour from 09:00 to 12:00, the third the largest. */
    private static RecordingSession chunked() {
        return ChunkedSessions.startingAt(CREATED, Duration.ofMinutes(15))
                .compressed(10, 20, 90, 30, 20, 10, 20, 30, 40, 10, 20, 30)
                .build();
    }

    private static WindowAnswer.Chosen chosen(WindowAnswer answer) {
        return assertInstanceOf(WindowAnswer.Chosen.class, answer);
    }

    private static McpInputResponse accepted(String contentJson) {
        return new McpInputResponse(McpInputResponse.Action.ACCEPT, (ObjectNode) Json.readTree(contentJson));
    }

    private static WindowAnswer read(RecordingSession session, String contentJson) {
        return QUESTION.read(accepted(contentJson), subject(session));
    }

    private static JsonNode request(McpToolOutcome.InputRequired asked) {
        assertEquals(Set.of(DownloadWindowQuestion.KEY), asked.requests().keySet());
        return asked.requests().get(DownloadWindowQuestion.KEY).toJson();
    }

    private static JsonNode schema(McpToolOutcome.InputRequired asked) {
        return request(asked).path("params").path("requestedSchema");
    }

    private static String problemOf(WindowAnswer answer) {
        return assertInstanceOf(WindowAnswer.Incomplete.class, answer).problem();
    }

    @Nested
    class Asks {

        private final DownloadWindowQuestion question = new DownloadWindowQuestion(Duration.ofHours(1), GIB);

        @Test
        void notForASessionExactlyAsLongAsTheThreshold() {
            assertFalse(question.asks(session(FINISHED.minus(Duration.ofHours(1)), FINISHED, 10), NOW));
        }

        @Test
        void forASessionOneMillisecondLongerThanTheThreshold() {
            assertTrue(question.asks(session(FINISHED.minus(Duration.ofHours(1)).minusMillis(1), FINISHED, 10), NOW));
        }

        @Test
        void notForASessionExactlyAsBigAsTheThreshold() {
            assertFalse(question.asks(session(FINISHED.minusSeconds(60), FINISHED, GIB), NOW));
        }

        @Test
        void forASessionOneByteBiggerThanTheThreshold() {
            assertTrue(question.asks(session(FINISHED.minusSeconds(60), FINISHED, GIB + 1), NOW));
        }

        /** A session still recording has lasted until now, not until a finish it has not reached. */
        @Test
        void measuresASessionStillRecordingUpToNow() {
            assertTrue(question.asks(session(NOW.minus(Duration.ofMinutes(61)), null, 10), NOW));
            assertFalse(question.asks(session(NOW.minus(Duration.ofMinutes(59)), null, 10), NOW));
        }

        @Test
        void aSessionWithNoStartIsJudgedBySizeAlone() {
            assertFalse(question.asks(session(null, FINISHED, 10), NOW));
            assertTrue(question.asks(session(null, FINISHED, GIB + 1), NOW));
        }

    }

    @Nested
    class Form {

        @Test
        void isAFormElicitationUnderTheDownloadWindowKey() {
            JsonNode request = request(QUESTION.ask(subject(large())));

            assertEquals("downloadWindow", DownloadWindowQuestion.KEY);
            assertEquals("elicitation/create", request.path("method").asString());
            assertEquals("form", request.path("params").path("mode").asString());
        }

        @Test
        void theMessageStatesTheSessionWhereItLivesItsSpanDurationAndSize() {
            String message = request(QUESTION.ask(subject(large()))).path("params").path("message").asString();

            for (String expected : List.of("checkout-api", "session-1", "production", "checkout",
                    CREATED.toString(), FINISHED.toString(), "3h0m", "2.0GB")) {
                assertTrue(message.contains(expected), expected + " missing from: " + message);
            }
        }

        /** The chunk length is measured from the session, never assumed. */
        @Test
        void theMessageStatesTheMeasuredChunkLength() {
            String message = request(QUESTION.ask(subject(chunked()))).path("params").path("message").asString();

            assertTrue(message.contains("in chunks of about 15m0s"), message);
        }

        @Test
        void theMessageSaysASessionIsStillRecording() {
            String message = request(QUESTION.ask(subject(session(CREATED, null, 2 * GIB))))
                    .path("params").path("message").asString();

            assertTrue(message.contains("still recording"), message);
            assertTrue(message.contains(NOW.toString()), message);
        }

        /** The specification allows a flat object of primitive properties and nothing else. */
        @Test
        void isAFlatObjectOfPrimitiveProperties() {
            JsonNode schema = schema(QUESTION.ask(subject(chunked())));

            assertEquals("object", schema.path("type").asString());
            assertEquals(List.of("window", "minutes", "at", "start", "end"),
                    List.copyOf(schema.path("properties").propertyNames()));
            for (JsonNode property : schema.path("properties")) {
                assertTrue(PRIMITIVE_TYPES.contains(property.path("type").asString()), property.toString());
                assertFalse(property.has("properties"), property.toString());
                assertFalse(property.has("items"), property.toString());
                for (JsonNode choice : property.path("oneOf")) {
                    assertEquals(CHOICE_KEYS, Set.copyOf(choice.propertyNames()), choice.toString());
                }
            }
            assertEquals(List.of("window"), strings(schema.path("required")));
        }

        @Test
        void offersEveryWindowTheSessionCanAnswerByNameDefaultingToTheLastMinutes() {
            JsonNode window = schema(QUESTION.ask(subject(chunked()))).path("properties").path("window");

            assertEquals(List.of("WHOLE", "LAST_MINUTES", "STARTUP", "LATEST", "PEAK", "BEFORE", "AROUND", "CUSTOM"),
                    values(window));
            assertEquals("LAST_MINUTES", window.path("default").asString());
        }

        @Test
        void titlesCarryTheSessionsOwnFigures() {
            List<String> titles = titles(schema(QUESTION.ask(subject(chunked()))).path("properties").path("window"));

            assertTrue(titles.contains("The last N minutes (set Minutes; 60 is 11:00–12:00 UTC)"), titles.toString());
            assertTrue(titles.contains("Startup (09:00–09:15 UTC, 10B)"), titles.toString());
            assertTrue(titles.contains("Peak (09:30–09:45 UTC, 90B, 4.5× median)"), titles.toString());
            assertTrue(titles.contains("Latest finished chunk (11:45–12:00 UTC, 30B)"), titles.toString());
        }

        /** No compressed chunk, no peak; a first chunk the cleaner removed, no startup. */
        @Test
        void offersOnlyWhatTheSessionCanAnswer() {
            RecordingSession trimmed = ChunkedSessions.startingAt(CREATED, Duration.ofMinutes(15))
                    .raw(10, 20, 30, 40, 50).withoutOldest(3).build();

            List<String> values = values(schema(QUESTION.ask(subject(trimmed))).path("properties").path("window"));

            assertFalse(values.contains("PEAK"), values.toString());
            assertFalse(values.contains("STARTUP"), values.toString());
            assertTrue(values.contains("LATEST"), values.toString());
        }

        @Test
        void minutesDefaultToTheLastHour() {
            JsonNode minutes = schema(QUESTION.ask(subject(large()))).path("properties").path("minutes");

            assertEquals("integer", minutes.path("type").asString());
            assertEquals(1, minutes.path("minimum").asLong());
            assertEquals(DownloadWindow.DEFAULT_MINUTES, minutes.path("default").asLong());
        }

        @Test
        void atStartAndEndAreDateTimesDefaultingToTheSessionsSpan() {
            JsonNode properties = schema(QUESTION.ask(subject(large()))).path("properties");

            assertEquals("date-time", properties.path("at").path("format").asString());
            assertEquals(FINISHED.toString(), properties.path("at").path("default").asString());
            assertEquals(CREATED.toString(), properties.path("start").path("default").asString());
            assertEquals(FINISHED.toString(), properties.path("end").path("default").asString());
        }

        @Test
        void theEndOfASessionStillRecordingDefaultsToNow() {
            JsonNode properties = schema(QUESTION.ask(subject(session(CREATED, null, 2 * GIB)))).path("properties");

            assertEquals(NOW.toString(), properties.path("end").path("default").asString());
        }

        @Test
        void askingAgainStatesTheProblemFirst() {
            String message = request(QUESTION.ask(subject(large()), "Choose one of the windows."))
                    .path("params").path("message").asString();

            assertTrue(message.startsWith("Choose one of the windows."), message);
            assertTrue(message.contains("checkout-api"), message);
        }

        private static List<String> values(JsonNode window) {
            List<String> values = new ArrayList<>();
            window.path("oneOf").forEach(choice -> values.add(choice.path("const").asString()));
            return values;
        }

        private static List<String> titles(JsonNode window) {
            List<String> titles = new ArrayList<>();
            window.path("oneOf").forEach(choice -> titles.add(choice.path("title").asString()));
            return titles;
        }

        private static List<String> strings(JsonNode array) {
            List<String> values = new ArrayList<>();
            array.forEach(value -> values.add(value.asString()));
            return values;
        }
    }

    /** An answer is the window chosen with the fields it takes; resolving it is DownloadWindow's. */
    @Nested
    class Answers {

        @Test
        void theLastMinutesWithTheirMinutes() {
            WindowAnswer.Chosen answer = chosen(read(large(), "{\"window\":\"LAST_MINUTES\",\"minutes\":10}"));

            assertEquals(DownloadWindow.LAST_MINUTES, answer.window());
            assertEquals(10, answer.given().minutes());
        }

        @Test
        void theLastMinutesWithoutMinutesLeaveTheDefaultToTheWindow() {
            WindowAnswer.Chosen answer = chosen(read(large(), "{\"window\":\"LAST_MINUTES\"}"));

            assertNull(answer.given().minutes());
        }

        @Test
        void aWholeNumberOfMinutesSentAsAFloatIsAccepted() {
            assertEquals(10, chosen(read(large(), "{\"window\":\"LAST_MINUTES\",\"minutes\":10.0}"))
                    .given().minutes());
        }

        @Test
        void beforeReadsItsMomentAndMinutes() {
            WindowAnswer.Chosen answer = chosen(read(large(),
                    "{\"window\":\"BEFORE\",\"at\":\"2026-03-01T10:00:00Z\",\"minutes\":20}"));

            assertEquals(Instant.parse("2026-03-01T10:00:00Z").toEpochMilli(), answer.given().atEpochMs());
            assertEquals(20, answer.given().minutes());
        }

        /** Fields the chosen window does not take are not read, whatever the form filled them with. */
        @Test
        void fieldsTheWindowDoesNotTakeAreIgnored() {
            WindowAnswer.Chosen answer = chosen(read(large(),
                    "{\"window\":\"WHOLE\",\"minutes\":60,\"at\":\"not a time\",\"start\":7}"));

            assertEquals(WindowArguments.NONE, answer.given());
        }

        @Test
        void aCustomWindow() {
            WindowAnswer.Chosen answer = chosen(read(large(),
                    "{\"window\":\"CUSTOM\",\"start\":\"2026-03-01T10:00:00Z\",\"end\":\"2026-03-01T10:30:00Z\"}"));

            assertEquals(Instant.parse("2026-03-01T10:00:00Z").toEpochMilli(), answer.given().startEpochMs());
            assertEquals(Instant.parse("2026-03-01T10:30:00Z").toEpochMilli(), answer.given().endEpochMs());
        }

        @Test
        void aDeclineIsNotAnswered() {
            WindowAnswer answer = QUESTION.read(
                    new McpInputResponse(McpInputResponse.Action.DECLINE, null), subject(large()));

            assertTrue(assertInstanceOf(WindowAnswer.NotAnswered.class, answer).reason().contains("declined"));
        }

        @Test
        void aCancelIsNotAnswered() {
            WindowAnswer answer = QUESTION.read(
                    new McpInputResponse(McpInputResponse.Action.CANCEL, null), subject(large()));

            assertTrue(assertInstanceOf(WindowAnswer.NotAnswered.class, answer).reason().contains("dismissed"));
        }
    }

    @Nested
    class IncompleteAnswers {

        @Test
        void anAcceptWithNoContent() {
            WindowAnswer answer = QUESTION.read(
                    new McpInputResponse(McpInputResponse.Action.ACCEPT, null), subject(large()));

            assertTrue(problemOf(answer).contains("window"));
        }

        @Test
        void noWindowChosen() {
            assertTrue(problemOf(read(large(), "{}")).contains("window"));
        }

        @Test
        void aWindowThatIsNoneOfTheChoices() {
            String problem = problemOf(read(large(), "{\"window\":\"lastHour\"}"));

            assertTrue(problem.contains("'lastHour'"), problem);
            assertTrue(problem.contains("LAST_MINUTES"), problem);
        }

        /** A window the session could not offer is not one of its choices. */
        @Test
        void aWindowTheSessionDidNotOffer() {
            assertTrue(problemOf(read(large(), "{\"window\":\"PEAK\"}")).contains("'PEAK'"));
        }

        @Test
        void aWindowThatIsNotAString() {
            assertTrue(problemOf(read(large(), "{\"window\":3}")).contains("window"));
        }

        @Test
        void minutesThatAreNotANumber() {
            assertTrue(problemOf(read(large(), "{\"window\":\"LAST_MINUTES\",\"minutes\":\"ten\"}"))
                    .contains("minutes"));
        }

        @Test
        void minutesThatAreNotWhole() {
            assertTrue(problemOf(read(large(), "{\"window\":\"LAST_MINUTES\",\"minutes\":2.5}"))
                    .contains("minutes"));
        }

        @Test
        void zeroMinutes() {
            assertTrue(problemOf(read(large(), "{\"window\":\"LAST_MINUTES\",\"minutes\":0}"))
                    .contains("minutes"));
        }

        @Test
        void minutesBeyondTheRangeOfALong() {
            assertTrue(problemOf(read(large(),
                    "{\"window\":\"LAST_MINUTES\",\"minutes\":99999999999999999999999}")).contains("minutes"));
        }

        @Test
        void aMomentThatIsAnObject() {
            assertTrue(problemOf(read(large(), "{\"window\":\"AROUND\",\"at\":{}}")).contains("at"));
        }

        @Test
        void aCustomEndThatIsAnArray() {
            assertTrue(problemOf(read(large(), "{\"window\":\"CUSTOM\",\"end\":[]}")).contains("end"));
        }

        @Test
        void aCustomStartThatIsANumber() {
            assertTrue(problemOf(read(large(), "{\"window\":\"CUSTOM\",\"start\":1}")).contains("start"));
        }

        @Test
        void aCustomStartThatIsNotADateTime() {
            String problem = problemOf(read(large(), "{\"window\":\"CUSTOM\",\"start\":\"yesterday\"}"));

            assertTrue(problem.contains("'yesterday'"), problem);
        }
    }
}
