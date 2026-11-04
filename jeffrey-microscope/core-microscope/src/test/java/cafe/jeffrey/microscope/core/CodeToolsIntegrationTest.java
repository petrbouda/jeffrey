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
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static cafe.jeffrey.test.recording.CheckoutRecording.BASELINE;
import static cafe.jeffrey.test.recording.CheckoutRecording.CANDIDATE;
import static cafe.jeffrey.test.recording.CheckoutRecording.EXECUTION_SAMPLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The code tools on the checkout runs: a coding agent holding the workload's source asks by the names
 * in it -- the package {@code com.acme.}, the nested {@code Checkout.PriceCalculator} as the source
 * spells it -- and the figures are held to SQL over the same profile database.
 */
class CodeToolsIntegrationTest extends CheckoutVolumeMicroscope {

    private static final String OWN_PACKAGE = "com.acme.";
    private static final String RUNTIME_NESTED_SEPARATOR = "$";
    private static final String FORMAT_LINE = "com.acme.checkout.Checkout.PriceCalculator#formatLine";
    private static final String FORMAT_LINE_RUNTIME = "com.acme.checkout.Checkout$PriceCalculator#formatLine";
    private static final String DISCOUNT_CLASS = "com.acme.checkout.Checkout.CouponParser";
    private static final String NUMBER_FORMAT = "java.lang.NumberFormatException";
    private static final String JAVA_FRAME_TYPES = "('JIT compiled', 'Interpreted', 'Inlined', 'C1 compiled')";

    /** Every CPU sample with a stack, as the graph reads them. */
    private static final String CPU_SAMPLES = """
            SELECT SUM(e.samples) FROM events e JOIN stacktraces s ON s.stacktrace_hash = e.stacktrace_hash
            WHERE e.event_type = 'jdk.ExecutionSample'""";

    /** The CPU samples whose stack holds at least one Java frame of the workload's own package. */
    private static final String OWN_CPU_SAMPLES = CPU_SAMPLES + """
             AND list_has_any(s.frame_hashes, (SELECT list(frame_hash) FROM frames
                 WHERE starts_with(class_name, 'com.acme.') AND hidden_class_id IS NULL
                 AND frame_type IN""" + " " + JAVA_FRAME_TYPES + "))";

    private static ArrayNode own() {
        return Json.createArray().add(OWN_PACKAGE);
    }

    @Nested
    class Hotspots {

        @Test
        void chargesEverySampleWithOwnCodeToExactlyOneOwnMethod() {
            JsonNode hotspots = answer("code_hotspots", CANDIDATE, Json.createObject()
                    .put("eventType", EXECUTION_SAMPLE)
                    .put("limit", 50)
                    .set("framePrefixes", own()));

            assertEquals("OK", hotspots.path("status").asString());
            assertEquals("GIVEN", hotspots.path("framePrefixesSource").asString());
            assertEquals(Long.parseLong(scalar(CANDIDATE, CPU_SAMPLES)), hotspots.path("total").asLong());
            long chargedToOwn = Long.parseLong(scalar(CANDIDATE, OWN_CPU_SAMPLES));
            assertEquals(chargedToOwn, hotspots.path("chargedToOwnCode").asLong());

            assertEquals(0, hotspots.path("omittedMethods").asInt());
            long charged = 0;
            for (JsonNode method : hotspots.path("methods")) {
                assertTrue(method.path("className").asString().startsWith(OWN_PACKAGE), method.toString());
                charged += method.path("charged").asLong();
            }
            assertEquals(chargedToOwn, charged);
        }

        @Test
        void namesEachMethodAsItsSourceSpellsItAndThatNameFindsItsFrame() {
            JsonNode hotspots = answer("code_hotspots", CANDIDATE, Json.createObject()
                    .put("eventType", EXECUTION_SAMPLE)
                    .put("limit", 50)
                    .set("framePrefixes", own()));

            for (JsonNode method : hotspots.path("methods")) {
                String reference = method.path("reference").asString();
                assertFalse(reference.contains(RUNTIME_NESTED_SEPARATOR), method.toString());
                MethodKey frame = new MethodKey(method.path("className").asString(), method.path("methodName").asString());
                assertTrue(MethodRef.parse(reference).matcher().matches(frame), method.toString());
            }
            JsonNode offered = hotspots.path("followUp").path("nextTools").get(0);
            assertEquals("code_method", offered.path("tool").asString());
            JsonNode top = hotspots.path("methods").get(0);
            assertEquals(top.path("reference").asString(), offered.path("arguments").path("className").asString()
                    + "#" + offered.path("arguments").path("methodName").asString());
        }

        @Test
        void suggestsTheWorkloadsPackageWhenNoneIsGiven() {
            JsonNode hotspots = answer("code_hotspots", CANDIDATE, Json.createObject()
                    .put("eventType", EXECUTION_SAMPLE));

            assertEquals("SUGGESTED", hotspots.path("framePrefixesSource").asString());
            assertTrue(names(hotspots.path("suggestedFramePrefixes")).contains(OWN_PACKAGE),
                    hotspots.path("suggestedFramePrefixes").toString());
        }

        @Test
        void anEventTypeTheRunDidNotRecordIsNotRecorded() {
            JsonNode hotspots = answer("code_hotspots", CANDIDATE, Json.createObject()
                    .put("eventType", "jdk.CPUTimeSample")
                    .set("framePrefixes", own()));

            assertEquals("NOT_RECORDED", hotspots.path("status").asString());
            assertTrue(hotspots.path("methods").isEmpty());
        }
    }

    @Nested
    class Method {

        @Test
        void readsANestedClassBySourceSpellingAndPlacesItInTheRequest() {
            JsonNode method = answer("code_method", CANDIDATE, Json.createObject()
                    .put("className", "com.acme.checkout.Checkout.PriceCalculator")
                    .put("methodName", "formatLine")
                    .put("eventType", EXECUTION_SAMPLE)
                    .set("framePrefixes", own()));

            assertEquals("OK", method.path("status").asString());
            JsonNode cpu = method.path("kinds").get(0);
            List<String> matched = new ArrayList<>();
            cpu.path("matchedFrames").forEach(frame -> matched.add(frame.path("frame").asString()));
            assertTrue(matched.contains(FORMAT_LINE_RUNTIME), matched.toString());
            assertTrue(cpu.path("inclusive").asLong() > 0);
            assertTrue(cpu.path("ownCallers").size() > 0, cpu.toString());

            assertEquals("READ", method.path("operations").path("state").asString());
        }

        @Test
        void namesTheExceptionsThrownUnderTheMethod() {
            JsonNode method = answer("code_method", BASELINE, Json.createObject()
                    .put("className", DISCOUNT_CLASS)
                    .put("methodName", "discount"));

            assertEquals("READ", method.path("exceptions").path("state").asString());
            List<String> thrown = new ArrayList<>();
            method.path("exceptions").path("thrown").forEach(row -> thrown.add(row.path("thrownClass").asString()));
            assertTrue(thrown.contains(NUMBER_FORMAT), thrown.toString());
        }

        @Test
        void aNestedClassInSourceSpellingFindsItsOwnMethodsAsNearest() {
            JsonNode method = answer("code_method", BASELINE, Json.createObject()
                    .put("className", "com.acme.checkout.Checkout.PriceCalculator")
                    .put("methodName", "formatLines"));

            assertEquals("NOT_SEEN", method.path("status").asString());
            assertFalse(method.path("nearestFrames").isEmpty(), method.toString());
            method.path("nearestFrames").forEach(frame -> assertTrue(
                    frame.asString().startsWith("com.acme.checkout.Checkout$PriceCalculator#"), frame.asString()));
        }

        @Test
        void aMethodNoSampleRanIsNotSeenWithItsClassmatesAsNearest() {
            JsonNode method = answer("code_method", BASELINE, Json.createObject()
                    .put("className", "com.acme.checkout.Checkout")
                    .put("methodName", "refundEverything"));

            assertEquals("NOT_SEEN", method.path("status").asString());
            assertTrue(method.path("nearestFrames").size() > 0, method.toString());
            method.path("nearestFrames").forEach(frame ->
                    assertTrue(frame.asString().startsWith("com.acme.checkout.Checkout"), frame.asString()));
        }
    }

    @Nested
    class Comparison {

        @Test
        void aMethodNamedWithoutItsNestedClassIsNotSeenAndPointsToTheOneThatRan() {
            ObjectNode arguments = Json.createObject()
                    .put("baselineProfileId", profile(BASELINE))
                    .put("eventType", EXECUTION_SAMPLE);
            arguments.set("methods", Json.createArray().add("com.acme.checkout.Checkout#formatLine"));
            arguments.set("framePrefixes", own());
            JsonNode comparison = answer("compare_methods", CANDIDATE, arguments);
            JsonNode move = comparison.path("methods").get(0);

            assertEquals("NOT_SEEN", move.path("movement").asString(), move::toString);
            assertEquals(FORMAT_LINE, move.path("nearestReferences").get(0).asString(), move::toString);
            JsonNode again = comparison.path("followUp").path("nextTools").get(0);
            assertEquals("compare_methods", again.path("tool").asString(), again::toString);
            assertEquals(FORMAT_LINE, again.path("arguments").path("methods").get(0).asString(), again::toString);
        }

        @Test
        void aMethodNamedWithoutItsPackageFindsTheClassInItsPackage() {
            ObjectNode arguments = Json.createObject()
                    .put("baselineProfileId", profile(BASELINE))
                    .put("eventType", EXECUTION_SAMPLE);
            arguments.set("methods", Json.createArray().add("Checkout.PriceCalculator#formatLine"));
            JsonNode move = answer("compare_methods", CANDIDATE, arguments).path("methods").get(0);

            assertEquals("NOT_SEEN", move.path("movement").asString(), move::toString);
            assertEquals(FORMAT_LINE, move.path("nearestReferences").get(0).asString(), move::toString);
        }

        @Test
        void theCandidatesRegressionIsTheMethodItsDiffTouched() {
            ObjectNode arguments = Json.createObject()
                    .put("baselineProfileId", profile(BASELINE))
                    .put("eventType", EXECUTION_SAMPLE);
            arguments.set("methods", Json.createArray().add(FORMAT_LINE));
            arguments.set("framePrefixes", own());
            JsonNode comparison = answer("compare_methods", CANDIDATE, arguments);

            assertEquals("OK", comparison.path("status").asString());
            JsonNode move = comparison.path("methods").get(0);
            assertEquals(FORMAT_LINE, move.path("reference").asString());
            assertTrue(List.of("MORE", "NEW").contains(move.path("movement").asString()), move.toString());
            assertTrue(move.path("inclusiveDelta").asDouble() > 0, move.toString());
            assertTrue(names(move.path("primaryFrames")).contains(FORMAT_LINE_RUNTIME), move.toString());

            assertTrue(move.path("nearestReferences").isEmpty(), move.toString());

            assertFalse(comparison.path("unlistedMovers").isEmpty(), comparison::toString);
            for (JsonNode mover : comparison.path("unlistedMovers")) {
                String[] frame = mover.path("frame").asString().split("#", 2);
                MethodRef reference = MethodRef.parse(mover.path("reference").asString());
                assertTrue(reference.matcher().matches(new MethodKey(frame[0], frame[1])), mover.toString());
            }
        }
    }

    @Nested
    class Provenance {

        private static final String COMMIT = "3017ffe0123456789abcdef0123456789abcdef0";
        private static final Path RUN =
                Path.of("target", "code-tools-it", UUID.randomUUID().toString()).toAbsolutePath();

        @Test
        void theCommitAnImportWasGivenComesBackWithTheProfile() throws IOException {
            Files.createDirectories(RUN);
            Path file = Files.write(RUN.resolve("checkout-built.jfr"), BASELINE.bytes());

            JsonNode imported = microscope.answer("recordings_analyzeFile", Json.createObject()
                    .put("path", file.toString())
                    .put("force", true)
                    .put("commit", COMMIT)
                    .put("dirty", false));
            assertEquals("READY", imported.path("status").asString(), imported::toString);

            JsonNode profile = microscope.answer("profiles_get",
                    Json.createObject().put("profileId", imported.path("profileId").asString()));
            assertEquals(COMMIT, profile.path("recordingCommit").asString());
            assertEquals("TAG", profile.path("recordingCommitSource").asString());
            assertFalse(profile.path("recordingDirty").asBoolean(true));
        }

        @Test
        void aRecordingNobodyTaggedHasNoCommitRatherThanAGuess() {
            JsonNode profile = microscope.answer("profiles_get",
                    Json.createObject().put("profileId", profile(CANDIDATE)));

            assertTrue(profile.path("recordingCommit").isNull(), profile.toString());
            assertTrue(profile.path("recordingCommitSource").isNull(), profile.toString());
        }
    }

    private static List<String> names(JsonNode array) {
        List<String> names = new ArrayList<>();
        array.forEach(node -> names.add(node.asString()));
        return names;
    }
}
