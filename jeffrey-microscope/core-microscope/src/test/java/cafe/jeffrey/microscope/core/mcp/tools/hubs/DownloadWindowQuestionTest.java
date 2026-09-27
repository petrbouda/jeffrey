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

    private static DownloadWindowQuestion.Subject subject(RecordingSession session) {
        return new DownloadWindowQuestion.Subject(session, "production", "checkout", NOW);
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
            JsonNode schema = schema(QUESTION.ask(subject(large())));

            assertEquals("object", schema.path("type").asString());
            assertEquals(List.of("window", "minutes", "start", "end"), List.copyOf(schema.path("properties").propertyNames()));
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
        void offersTheFourWindowsAsTitledChoicesDefaultingToTheLastHour() {
            JsonNode window = schema(QUESTION.ask(subject(large()))).path("properties").path("window");

            List<String> values = new ArrayList<>();
            List<String> titles = new ArrayList<>();
            window.path("oneOf").forEach(choice -> {
                values.add(choice.path("const").asString());
                titles.add(choice.path("title").asString());
            });
            assertEquals(List.of("lastHour", "lastMinutes", "whole", "custom"), values);
            assertEquals("The last hour (11:00–12:00 UTC)", titles.get(0));
            assertTrue(titles.get(2).contains("(2.0GB)"), titles.get(2));
            assertEquals("lastHour", window.path("default").asString());
        }

        @Test
        void theLastHourOfASessionStillRecordingEndsNow() {
            JsonNode window = schema(QUESTION.ask(subject(session(CREATED, null, 2 * GIB))))
                    .path("properties").path("window");

            assertEquals("The last hour (11:30–12:30 UTC)", window.path("oneOf").get(0).path("title").asString());
        }

        @Test
        void minutesRunFromOneToTheSessionsLengthDefaultingToFifteen() {
            JsonNode minutes = schema(QUESTION.ask(subject(large()))).path("properties").path("minutes");

            assertEquals("integer", minutes.path("type").asString());
            assertEquals(1, minutes.path("minimum").asLong());
            assertEquals(180, minutes.path("maximum").asLong());
            assertEquals(15, minutes.path("default").asLong());
        }

        @Test
        void theDefaultMinutesNeverExceedAShortSession() {
            JsonNode minutes = schema(QUESTION.ask(subject(session(FINISHED.minusSeconds(600), FINISHED, 2 * GIB))))
                    .path("properties").path("minutes");

            assertEquals(10, minutes.path("maximum").asLong());
            assertEquals(10, minutes.path("default").asLong());
        }

        @Test
        void startAndEndAreDateTimesDefaultingToTheSessionsSpan() {
            JsonNode properties = schema(QUESTION.ask(subject(large()))).path("properties");

            assertEquals("date-time", properties.path("start").path("format").asString());
            assertEquals(CREATED.toString(), properties.path("start").path("default").asString());
            assertEquals("date-time", properties.path("end").path("format").asString());
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

        private static List<String> strings(JsonNode array) {
            List<String> values = new ArrayList<>();
            array.forEach(value -> values.add(value.asString()));
            return values;
        }
    }

    @Nested
    class Answers {

        @Test
        void theLastHourEndsAtTheSessionsFinish() {
            WindowAnswer answer = read(large(), "{\"window\":\"lastHour\"}");

            assertEquals(new WindowAnswer.Chosen(
                    FINISHED.minus(Duration.ofHours(1)).toEpochMilli(), FINISHED.toEpochMilli()), answer);
        }

        @Test
        void theLastHourOfASessionStillRecordingEndsNow() {
            WindowAnswer answer = read(session(CREATED, null, 2 * GIB), "{\"window\":\"lastHour\"}");

            assertEquals(new WindowAnswer.Chosen(
                    NOW.minus(Duration.ofHours(1)).toEpochMilli(), NOW.toEpochMilli()), answer);
        }

        /** A last hour that reaches back past a finished session's start is all of it: its local copy. */
        @Test
        void theLastHourOfAFinishedSessionShorterThanAnHourIsTheWholeSession() {
            RecordingSession short_ = session(FINISHED.minusSeconds(600), FINISHED, 2 * GIB);

            assertInstanceOf(WindowAnswer.Whole.class, read(short_, "{\"window\":\"lastHour\"}"));
        }

        @Test
        void theLastMinutesSpanningAFinishedSessionAreTheWholeSession() {
            assertInstanceOf(WindowAnswer.Whole.class, read(large(), "{\"window\":\"lastMinutes\",\"minutes\":180}"));
        }

        /** A session still recording has no settled whole: the clamped stretch stays a window up to now. */
        @Test
        void theLastHourOfALiveSessionShorterThanAnHourStartsWithTheSession() {
            RecordingSession live = session(NOW.minusSeconds(600), null, 2 * GIB);

            assertEquals(new WindowAnswer.Chosen(NOW.minusSeconds(600).toEpochMilli(), NOW.toEpochMilli()),
                    read(live, "{\"window\":\"lastHour\"}"));
        }

        @Test
        void theLastMinutesEndAtTheSessionsFinish() {
            WindowAnswer answer = read(large(), "{\"window\":\"lastMinutes\",\"minutes\":20}");

            assertEquals(new WindowAnswer.Chosen(
                    FINISHED.minus(Duration.ofMinutes(20)).toEpochMilli(), FINISHED.toEpochMilli()), answer);
        }

        /** Some hosts send every number as a float; a whole one is still a whole number of minutes. */
        @Test
        void aWholeNumberOfMinutesSentAsAFloatIsAccepted() {
            WindowAnswer answer = read(large(), "{\"window\":\"lastMinutes\",\"minutes\":20.0}");

            assertInstanceOf(WindowAnswer.Chosen.class, answer);
        }

        @Test
        void theWholeSession() {
            assertInstanceOf(WindowAnswer.Whole.class, read(large(), "{\"window\":\"whole\"}"));
        }

        @Test
        void aCustomWindow() {
            WindowAnswer answer = read(large(),
                    "{\"window\":\"custom\",\"start\":\"2026-03-01T10:00:00Z\",\"end\":\"2026-03-01T10:30:00+00:00\"}");

            assertEquals(new WindowAnswer.Chosen(
                    Instant.parse("2026-03-01T10:00:00Z").toEpochMilli(),
                    Instant.parse("2026-03-01T10:30:00Z").toEpochMilli()), answer);
        }

        /** An absent start or end is the form's default: the session's own start or end. */
        @Test
        void aCustomWindowWithoutItsEndsTakesTheSessionsSpan() {
            WindowAnswer answer = read(large(),
                    "{\"window\":\"custom\",\"start\":\"2026-03-01T10:00:00Z\"}");

            assertEquals(new WindowAnswer.Chosen(
                    Instant.parse("2026-03-01T10:00:00Z").toEpochMilli(), FINISHED.toEpochMilli()), answer);
        }

        @Test
        void aDeclineIsNotAnswered() {
            WindowAnswer answer = QUESTION.read(
                    new McpInputResponse(McpInputResponse.Action.DECLINE, null), subject(large()));

            assertFalse(assertInstanceOf(WindowAnswer.NotAnswered.class, answer)
                    .reason().isBlank());
        }

        @Test
        void aCancelIsNotAnswered() {
            WindowAnswer answer = QUESTION.read(
                    new McpInputResponse(McpInputResponse.Action.CANCEL, null), subject(large()));

            assertInstanceOf(WindowAnswer.NotAnswered.class, answer);
        }
    }

    /** What the user sent does not make a window: asked again, naming what is missing. */
    @Nested
    class IncompleteAnswers {

        @Test
        void anAcceptWithNoContent() {
            WindowAnswer answer = QUESTION.read(
                    new McpInputResponse(McpInputResponse.Action.ACCEPT, null), subject(large()));

            assertTrue(problemOf(answer).contains("window"), problemOf(answer));
        }

        @Test
        void noWindowChosen() {
            assertTrue(problemOf(read(large(), "{}")).contains("window"));
        }

        @Test
        void aWindowThatIsNoneOfTheChoices() {
            String problem = problemOf(read(large(), "{\"window\":\"yesterday\"}"));

            assertTrue(problem.contains("yesterday"), problem);
        }

        @Test
        void theLastMinutesWithoutMinutes() {
            assertTrue(problemOf(read(large(), "{\"window\":\"lastMinutes\"}")).contains("minutes"));
        }

        @Test
        void minutesThatAreNotANumber() {
            assertTrue(problemOf(read(large(), "{\"window\":\"lastMinutes\",\"minutes\":\"many\"}")).contains("minutes"));
        }

        @Test
        void minutesThatAreNotWhole() {
            assertTrue(problemOf(read(large(), "{\"window\":\"lastMinutes\",\"minutes\":2.5}")).contains("minutes"));
        }

        @Test
        void zeroMinutes() {
            String problem = problemOf(read(large(), "{\"window\":\"lastMinutes\",\"minutes\":0}"));

            assertTrue(problem.contains("180"), problem);
        }

        @Test
        void moreMinutesThanTheSessionLasted() {
            String problem = problemOf(read(large(), "{\"window\":\"lastMinutes\",\"minutes\":181}"));

            assertTrue(problem.contains("180"), problem);
        }

        /** Jackson reads these strictly; a value no long can hold is asked about, never thrown. */
        @Test
        void minutesTooLargeForAnyWholeNumber() {
            String problem = problemOf(read(large(), "{\"window\":\"lastMinutes\",\"minutes\":1e30}"));

            assertTrue(problem.contains("minutes"), problem);
        }

        @Test
        void minutesBeyondTheRangeOfALong() {
            String problem = problemOf(read(large(),
                    "{\"window\":\"lastMinutes\",\"minutes\":123456789012345678901234567890}"));

            assertTrue(problem.contains("minutes"), problem);
        }

        @Test
        void hugeMinutesOnASessionWithNoStart() {
            String problem = problemOf(read(session(null, FINISHED, 2 * GIB),
                    "{\"window\":\"lastMinutes\",\"minutes\":9223372036854775807}"));

            assertTrue(problem.contains("minutes"), problem);
        }

        @Test
        void aWindowThatIsNotAString() {
            assertTrue(problemOf(read(large(), "{\"window\":5}")).contains("window"));
            assertTrue(problemOf(read(large(), "{\"window\":{}}")).contains("window"));
        }

        @Test
        void aCustomStartThatIsAnObject() {
            String problem = problemOf(read(large(), "{\"window\":\"custom\",\"start\":{}}"));

            assertTrue(problem.contains("{}"), problem);
        }

        @Test
        void aCustomEndThatIsAnArray() {
            String problem = problemOf(read(large(), "{\"window\":\"custom\",\"end\":[]}"));

            assertTrue(problem.contains("[]"), problem);
        }

        @Test
        void aCustomStartThatIsANumber() {
            String problem = problemOf(read(large(), "{\"window\":\"custom\",\"start\":1772355600000}"));

            assertTrue(problem.contains("1772355600000"), problem);
        }

        @Test
        void aCustomStartThatIsNotADateTime() {
            String problem = problemOf(read(large(), "{\"window\":\"custom\",\"start\":\"ten o'clock\"}"));

            assertTrue(problem.contains("ten o'clock"), problem);
        }

        @Test
        void aCustomWindowEndingBeforeItStarts() {
            String problem = problemOf(read(large(),
                    "{\"window\":\"custom\",\"start\":\"2026-03-01T11:00:00Z\",\"end\":\"2026-03-01T10:00:00Z\"}"));

            assertTrue(problem.contains("before"), problem);
        }

        @Test
        void aCustomWindowOutsideTheSession() {
            String problem = problemOf(read(large(),
                    "{\"window\":\"custom\",\"start\":\"2026-03-01T13:00:00Z\",\"end\":\"2026-03-01T14:00:00Z\"}"));

            assertTrue(problem.contains(CREATED.toString()), problem);
            assertTrue(problem.contains(FINISHED.toString()), problem);
        }
    }
}
