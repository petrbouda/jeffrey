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

package cafe.jeffrey.provisioner;

import cafe.jeffrey.provisioner.AsyncProfilerResolver.ResolvedProfiler;
import cafe.jeffrey.provisioner.feature.JvmFeature;
import cafe.jeffrey.shared.common.CliConstants;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AsyncProfilerResolverTest {

    private static final String PROFILER_PATH_LIBRARY = "/opt/jeffrey/libasyncProfiler.so";

    private static final AsyncProfilerResolver PROFILER_PATH_PRESENT = new AsyncProfilerResolver(path -> true);
    private static final AsyncProfilerResolver PROFILER_PATH_MISSING = new AsyncProfilerResolver(path -> false);

    @Nested
    class NoCommand {

        @Test
        void runsTheProfilerPathLibraryWithTheBuiltInOptions() {
            ResolvedProfiler resolved = PROFILER_PATH_PRESENT.resolve(null, PROFILER_PATH_LIBRARY);

            assertEquals(new JvmFeature.AsyncProfiler(PROFILER_PATH_LIBRARY, CliConstants.DEFAULT_PROFILER_OPTIONS),
                    resolved.feature());
            assertEquals(ProfilerSource.BUILT_IN, resolved.source());
        }

        @Test
        void treatsABlankCommandAsUnset() {
            assertEquals(ProfilerSource.BUILT_IN, PROFILER_PATH_PRESENT.resolve("   ", PROFILER_PATH_LIBRARY).source());
        }

        @Test
        void switchesProfilingOffWhenTheProfilerPathLibraryIsMissing() {
            ResolvedProfiler resolved = PROFILER_PATH_MISSING.resolve(null, PROFILER_PATH_LIBRARY);

            assertFalse(resolved.feature().enabled());
            assertEquals(ProfilerSource.DISABLED, resolved.source());
        }

        @Test
        void switchesProfilingOffWhenNoProfilerPathIsConfigured() {
            ResolvedProfiler resolved = PROFILER_PATH_PRESENT.resolve(null, null);

            assertFalse(resolved.feature().enabled());
            assertEquals(ProfilerSource.DISABLED, resolved.source());
        }
    }

    @Nested
    class OptionsOnly {

        @Test
        void runsTheOptionsOnTheProfilerPathLibrary() {
            ResolvedProfiler resolved = PROFILER_PATH_PRESENT.resolve("start,event=cpu", PROFILER_PATH_LIBRARY);

            assertEquals(new JvmFeature.AsyncProfiler(PROFILER_PATH_LIBRARY, "start,event=cpu"), resolved.feature());
            assertEquals(ProfilerSource.CONFIGURED_OPTIONS, resolved.source());
        }

        @Test
        void switchesProfilingOffWhenTheProfilerPathLibraryIsMissing() {
            assertEquals(ProfilerSource.DISABLED, PROFILER_PATH_MISSING.resolve("start,event=cpu", PROFILER_PATH_LIBRARY).source());
        }
    }

    @Nested
    class AgentPath {

        @Test
        void usesTheNamedLibraryAsGiven() {
            ResolvedProfiler resolved = PROFILER_PATH_PRESENT.resolve("-agentpath:/custom/lib.so=start,event=cpu", PROFILER_PATH_LIBRARY);

            assertEquals(new JvmFeature.AsyncProfiler("/custom/lib.so", "start,event=cpu"), resolved.feature());
            assertEquals(ProfilerSource.AGENT_PATH, resolved.source());
        }

        /** Whoever named the library owns the path; only the profiler-path one is looked up. */
        @Test
        void doesNotCheckTheNamedLibrary() {
            ResolvedProfiler resolved = PROFILER_PATH_MISSING.resolve("-agentpath:/custom/lib.so=start", null);

            assertEquals(new JvmFeature.AsyncProfiler("/custom/lib.so", "start"), resolved.feature());
            assertEquals(ProfilerSource.AGENT_PATH, resolved.source());
        }

        @Test
        void acceptsALibraryWithoutOptions() {
            ResolvedProfiler resolved = PROFILER_PATH_PRESENT.resolve("-agentpath:/custom/lib.so", PROFILER_PATH_LIBRARY);

            assertEquals(new JvmFeature.AsyncProfiler("/custom/lib.so", null), resolved.feature());
        }

        @Test
        void splitsAtTheFirstSeparatorOnly() {
            ResolvedProfiler resolved = PROFILER_PATH_PRESENT.resolve("-agentpath:/lib.so=start,event=cpu,file=/x", PROFILER_PATH_LIBRARY);

            assertEquals("start,event=cpu,file=/x", resolved.feature().options());
        }

        @Test
        void treatsTheProfilerPathPlaceholderAsTheProfilerPathLibrary() {
            ResolvedProfiler resolved = PROFILER_PATH_PRESENT.resolve(
                    "-agentpath:" + CliConstants.PROFILER_PATH + "=start", PROFILER_PATH_LIBRARY);

            assertEquals(new JvmFeature.AsyncProfiler(PROFILER_PATH_LIBRARY, "start"), resolved.feature());
            assertEquals(ProfilerSource.CONFIGURED_OPTIONS, resolved.source());
        }

        @Test
        void switchesProfilingOffWhenThePlaceholderLibraryIsMissing() {
            assertEquals(ProfilerSource.DISABLED,
                    PROFILER_PATH_MISSING.resolve("-agentpath:" + CliConstants.PROFILER_PATH + "=start", PROFILER_PATH_LIBRARY).source());
        }
    }

    @Nested
    class FileSystemCheck {

        @TempDir
        Path tempDir;

        @Test
        void findsALibraryThatExists() throws IOException {
            Path library = Files.createFile(tempDir.resolve("libasyncProfiler.so"));

            assertEquals(ProfilerSource.BUILT_IN,
                    new AsyncProfilerResolver().resolve(null, library.toString()).source());
        }

        @Test
        void missesALibraryThatDoesNotExist() {
            assertEquals(ProfilerSource.DISABLED,
                    new AsyncProfilerResolver().resolve(null, tempDir.resolve("missing.so").toString()).source());
        }

        @Test
        void missesADirectory() {
            assertEquals(ProfilerSource.DISABLED,
                    new AsyncProfilerResolver().resolve(null, tempDir.toString()).source());
        }
    }
}
