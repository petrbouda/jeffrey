/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.init;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.init.model.HeapDumpType;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FeatureBuilderTest {

    private static final Path SESSION_PATH = Path.of("/tmp/sessions/session-123");

    @Nested
    class EmptyBuilder {

        @Test
        void producesEmptyString() {
            String result = new FeatureBuilder().build(SESSION_PATH);
            assertEquals("", result);
        }
    }

    @Nested
    class DebugNonSafepoints {

        @Test
        void enabledProducesCorrectOptions() {
            String result = new FeatureBuilder()
                    .setDebugNonSafepointsEnabled(true)
                    .build(SESSION_PATH);

            assertTrue(result.contains("-XX:+UnlockDiagnosticVMOptions"));
            assertTrue(result.contains("-XX:+DebugNonSafepoints"));
        }

        @Test
        void disabledProducesNoOptions() {
            String result = new FeatureBuilder()
                    .setDebugNonSafepointsEnabled(false)
                    .build(SESSION_PATH);

            assertEquals("", result);
        }
    }

    @Nested
    class PerfCounters {

        @Test
        void enabledProducesCorrectOptions() {
            String result = new FeatureBuilder()
                    .setPerfCountersEnabled(true)
                    .build(SESSION_PATH);

            assertTrue(result.contains("-XX:+UsePerfData"));
            assertTrue(result.contains("-XX:PerfDataSaveFile="));
            assertTrue(result.contains(SESSION_PATH.toString()));
            assertTrue(result.contains(FeatureBuilder.PERF_COUNTERS_FILE));
        }

        @Test
        void disabledProducesNoOptions() {
            String result = new FeatureBuilder()
                    .setPerfCountersEnabled(false)
                    .build(SESSION_PATH);

            assertEquals("", result);
        }
    }

    @Nested
    class HeapDump {

        @Test
        void exitTypeProducesCorrectOptions() {
            String result = new FeatureBuilder()
                    .setHeapDumpEnabled(HeapDumpType.EXIT)
                    .build(SESSION_PATH);

            assertTrue(result.contains("-XX:+HeapDumpOnOutOfMemoryError"));
            assertTrue(result.contains("-XX:HeapDumpPath="));
            assertTrue(result.contains("-XX:+ExitOnOutOfMemoryError"));
            assertTrue(result.contains(SESSION_PATH.toString()));
            assertFalse(result.contains("-XX:+CrashOnOutOfMemoryError"));
        }

        @Test
        void crashTypeProducesCorrectOptions() {
            String result = new FeatureBuilder()
                    .setHeapDumpEnabled(HeapDumpType.CRASH)
                    .build(SESSION_PATH);

            assertTrue(result.contains("-XX:+HeapDumpOnOutOfMemoryError"));
            assertTrue(result.contains("-XX:HeapDumpPath="));
            assertTrue(result.contains("-XX:+CrashOnOutOfMemoryError"));
            assertTrue(result.contains("-XX:ErrorFile="));
            assertTrue(result.contains("hs-jvm-err.log"));
            assertTrue(result.contains(SESSION_PATH.toString()));
            assertFalse(result.contains("-XX:+ExitOnOutOfMemoryError"));
        }

        @Test
        void nullTypeProducesNoOptions() {
            String result = new FeatureBuilder()
                    .setHeapDumpEnabled(null)
                    .build(SESSION_PATH);

            assertEquals("", result);
        }
    }

    @Nested
    class JvmLogging {

        @Test
        void producesCorrectOptionsWithPathReplacement() {
            String command = "jfr*=trace:file=<<JEFFREY_CURRENT_SESSION>>/jfr-jvm.log";
            String result = new FeatureBuilder()
                    .setJvmLogging(command)
                    .build(SESSION_PATH);

            assertTrue(result.contains("-Xlog:"));
            assertTrue(result.contains("jfr*=trace:file="));
            assertTrue(result.contains(SESSION_PATH + "/jfr-jvm.log"));
            assertFalse(result.contains("<<JEFFREY_CURRENT_SESSION>>"));
        }

        @Test
        void nullLoggingProducesNoOptions() {
            String result = new FeatureBuilder()
                    .setJvmLogging(null)
                    .build(SESSION_PATH);

            assertEquals("", result);
        }

        @Test
        void blankLoggingProducesNoOptions() {
            String result = new FeatureBuilder()
                    .setJvmLogging("   ")
                    .build(SESSION_PATH);

            assertEquals("", result);
        }
    }

    @Nested
    class Agent {

        @Test
        void agentPathProducesAgentAndStreamingOptions() {
            String result = new FeatureBuilder()
                    .setAgentPath("/path/to/jeffrey-agent.jar")
                    .build(SESSION_PATH);

            assertTrue(result.contains("-javaagent:/path/to/jeffrey-agent.jar"));
            assertTrue(result.contains("-XX:FlightRecorderOptions:repository="));
            assertTrue(result.contains(FeatureBuilder.STREAMING_REPO_DIR));
            assertTrue(result.contains("preserve-repository=true"));
        }

        @Test
        void noAgentPathProducesNoOptions() {
            String result = new FeatureBuilder()
                    .build(SESSION_PATH);

            assertFalse(result.contains("-javaagent:"));
            assertFalse(result.contains("-XX:FlightRecorderOptions:repository="));
        }
    }

    @Nested
    class AdditionalJvmOptions {

        @Test
        void appendsCorrectly() {
            String additionalOptions = "-Xmx1200m -Xms1200m -XX:+UseG1GC";
            String result = new FeatureBuilder()
                    .setAdditionalJvmOptions(additionalOptions)
                    .build(SESSION_PATH);

            assertEquals(additionalOptions, result);
        }

        @Test
        void nullProducesNoOptions() {
            String result = new FeatureBuilder()
                    .setAdditionalJvmOptions(null)
                    .build(SESSION_PATH);

            assertEquals("", result);
        }

        @Test
        void blankProducesNoOptions() {
            String result = new FeatureBuilder()
                    .setAdditionalJvmOptions("   ")
                    .build(SESSION_PATH);

            assertEquals("", result);
        }
    }

    @Nested
    class MultipleFeatures {

        @Test
        void combinesAllFeaturesCorrectly() {
            String result = new FeatureBuilder()
                    .setPerfCountersEnabled(true)
                    .setHeapDumpEnabled(HeapDumpType.CRASH)
                    .setJvmLogging("jfr*=trace:file=<<JEFFREY_CURRENT_SESSION>>/jfr-jvm.log")
                    .setAgentPath("/path/to/jeffrey-agent.jar")
                    .setAdditionalJvmOptions("-Xmx1200m")
                    .build(SESSION_PATH);

            // All features should be present
            assertTrue(result.contains("-XX:+UsePerfData"));
            assertTrue(result.contains("-XX:+CrashOnOutOfMemoryError"));
            assertTrue(result.contains("-Xlog:"));
            assertTrue(result.contains("-javaagent:/path/to/jeffrey-agent.jar"));
            assertTrue(result.contains("-XX:FlightRecorderOptions:repository="));
            assertTrue(result.contains("-Xmx1200m"));
        }

        @Test
        void optionsAreSeparatedBySpaces() {
            String result = new FeatureBuilder()
                    .setPerfCountersEnabled(true)
                    .setAdditionalJvmOptions("-Xmx1200m")
                    .build(SESSION_PATH);

            // Should not have double spaces
            assertFalse(result.contains("  "));
            // Should be trimmed
            assertEquals(result, result.trim());
        }
    }
}
