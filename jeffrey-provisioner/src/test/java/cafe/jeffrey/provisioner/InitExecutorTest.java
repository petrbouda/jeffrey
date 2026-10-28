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

import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.JeffreyLayout;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of {@code init}: the directory tree it lays down, the marker files it
 * writes, and the two artefacts a JVM actually consumes.
 *
 * <p>Written to pin the observable output rather than the shape of the code producing it, so it
 * holds across restructuring. Generated ids are normalized out — everything else is compared
 * verbatim.
 */
class InitExecutorTest {

    private static final String WORKSPACE_REF_ID = "test-workspace";
    private static final String PROJECT_NAME = "my-service";
    private static final String INSTANCE_NAME = "instance-1";

    @TempDir
    Path tempDir;

    private Path jeffreyHome;
    private Path workspacesDir;
    private Path argFile;
    private Path envFile;
    private Path profilerLibrary;

    /** A run whose profiler-path library is in place, as jeffrey-jib bakes it. */
    private InitConfig config() throws IOException {
        return config(Map.of());
    }

    /** As {@link #config()}, plus the variables a test adds on top. */
    private InitConfig config(Map<String, String> extraEnv) throws IOException {
        profilerLibrary = tempDir.resolve("libasyncProfiler.so");
        if (Files.notExists(profilerLibrary)) {
            Files.createFile(profilerLibrary);
        }
        return config(profilerLibrary, extraEnv);
    }

    private InitConfig config(Path profilerPath) {
        return config(profilerPath, Map.of());
    }

    private InitConfig config(Path profilerPath, Map<String, String> extraEnv) {
        jeffreyHome = tempDir.resolve("jeffrey");
        workspacesDir = jeffreyHome.resolve(JeffreyLayout.WORKSPACES_DIR);
        argFile = tempDir.resolve("jvm.args");
        envFile = tempDir.resolve("jeffrey.env");

        Map<String, String> env = new HashMap<>(Map.of(
                "JEFFREY_HOME", jeffreyHome.toString(),
                "JEFFREY_PROJECT_NAME", PROJECT_NAME,
                "JEFFREY_WORKSPACE_REF_ID", WORKSPACE_REF_ID,
                "JEFFREY_INSTANCE_NAME", INSTANCE_NAME,
                "JEFFREY_ARG_FILE", argFile.toString(),
                "JEFFREY_ENV_FILE", envFile.toString(),
                "JEFFREY_PROFILER_PATH", profilerPath.toString()));
        env.putAll(extraEnv);

        return InitConfig.fromEnvironment(env::get);
    }

    private List<String> argFileOptions() throws IOException {
        return Files.readString(argFile).lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
    }

    private Path sessionPath() throws IOException {
        Path instancePath = workspacesDir.resolve(WORKSPACE_REF_ID).resolve(PROJECT_NAME).resolve(INSTANCE_NAME);
        try (Stream<Path> sessions = Files.list(instancePath)) {
            return sessions.filter(Files::isDirectory).findFirst().orElseThrow();
        }
    }

    /** Replaces the run's generated session directory name so content can be compared verbatim. */
    private String normalized(String content) throws IOException {
        return content.replace(sessionPath().getFileName().toString(), "<session>");
    }

    @Nested
    class DirectoryTree {

        @Test
        void laysDownWorkspaceProjectInstanceAndSession() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config());

            Path workspace = workspacesDir.resolve(WORKSPACE_REF_ID);
            Path project = workspace.resolve(PROJECT_NAME);
            Path instance = project.resolve(INSTANCE_NAME);

            assertTrue(Files.isDirectory(workspace), "workspace directory");
            assertTrue(Files.isDirectory(project), "project directory");
            assertTrue(Files.isDirectory(instance), "instance directory");
            assertTrue(Files.isDirectory(sessionPath()), "session directory");
        }

        @Test
        void createsNothingInsideTheSessionDirectory() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config());

            assertFalse(Files.exists(sessionPath().resolve("streaming-repo")));
            assertFalse(Files.exists(sessionPath().resolve(".heartbeat")), "no heartbeat directory any more");
        }

        /** A level is the directory names alone: no marker file is written anywhere below the workspace. */
        @Test
        void writesOnlyTheDirectories() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config());

            try (Stream<Path> tree = Files.walk(workspacesDir.resolve(WORKSPACE_REF_ID))) {
                List<Path> files = tree.filter(Files::isRegularFile).toList();
                assertEquals(List.of(), files, "the names say it all");
            }
        }
    }

    @Nested
    class GeneratedArtefacts {

        @Test
        void argFileCarriesOneOptionPerLine() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config());

            List<String> options = argFileOptions();

            assertTrue(options.stream().allMatch(option -> option.startsWith("-")),
                    () -> "every line must be a JVM option: " + options);
        }

        @Test
        void argFileArmsTheProfilerPathLibraryFirst() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config());

            String session = sessionPath().toString();
            assertEquals("-agentpath:" + profilerLibrary + "=start,alloc,lock,event=ctimer,jfrsync=default,"
                    + "loop=15m,chunksize=5m,file=" + session + "/profile-%t+0000.jfr", argFileOptions().getFirst());
        }

        /** A missing library would stop the JVM, so the session runs unprofiled instead. */
        @Test
        void argFileLeavesTheProfilerOutWhenTheProfilerPathLibraryIsMissing() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config(tempDir.resolve("missing.so")));

            List<String> options = argFileOptions();
            assertFalse(options.isEmpty(), "the other features are still written");
            assertTrue(options.stream().noneMatch(option -> option.startsWith("-agentpath:")),
                    () -> "the profiler must not be armed: " + options);
            assertTrue(Files.isDirectory(sessionPath()), "the session is still recorded");
        }

        @Test
        void argFileResolvesEveryPlaceholder() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config());

            String content = Files.readString(argFile);
            assertFalse(content.contains("<<"), () -> "unresolved placeholder in argfile: " + content);
            assertTrue(content.contains(sessionPath().toString()), "session path must be substituted");
        }

        @Test
        void envFileExportsTheSessionLayout() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config());

            String session = normalized(sessionPath().toString());
            List<String> layoutExports = normalized(Files.readString(envFile)).lines().toList();

            assertEquals(List.of(
                    "export JEFFREY_HOME=" + jeffreyHome,
                    "export JEFFREY_WORKSPACES=" + workspacesDir,
                    "export JEFFREY_CURRENT_WORKSPACE=" + workspacesDir.resolve(WORKSPACE_REF_ID),
                    "export JEFFREY_CURRENT_PROJECT="
                            + workspacesDir.resolve(WORKSPACE_REF_ID).resolve(PROJECT_NAME),
                    "export JEFFREY_CURRENT_SESSION=" + session,
                    "export JEFFREY_FILE_PATTERN=" + session + "/profile-%t+0000.jfr"),
                    layoutExports);
            assertTrue(Files.readString(envFile).endsWith("\n"), "env file must be newline-terminated");
        }
    }

    @Nested
    class RepeatedRuns {

        /** A second run reuses the project and instance and adds a session of its own. */
        @Test
        void secondRunAddsASessionAndKeepsTheProject() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config());
            new InitExecutor(Clock.systemUTC()).execute(config());

            Path instancePath = workspacesDir.resolve(WORKSPACE_REF_ID).resolve(PROJECT_NAME).resolve(INSTANCE_NAME);
            try (Stream<Path> sessions = Files.list(instancePath)) {
                assertEquals(2, sessions.filter(Files::isDirectory).count(), "one session per run");
            }
        }

        /** A session's place in its instance is its start time, carried in its name; no ordinal is written down. */
        @Test
        void eachSessionIsNamedForWhenItsRunStarted() throws Exception {
            Instant firstRun = Instant.parse("2026-03-01T10:00:00Z");
            Instant secondRun = Instant.parse("2026-03-01T11:00:00Z");
            new InitExecutor(Clock.fixed(firstRun, ZoneOffset.UTC)).execute(config());
            new InitExecutor(Clock.fixed(secondRun, ZoneOffset.UTC)).execute(config());

            Path instancePath = workspacesDir.resolve(WORKSPACE_REF_ID).resolve(PROJECT_NAME).resolve(INSTANCE_NAME);
            try (Stream<Path> sessions = Files.list(instancePath)) {
                List<Instant> starts = sessions
                        .filter(Files::isDirectory)
                        .map(session -> IDGenerator.timestampOf(session.getFileName().toString()).orElseThrow())
                        .sorted()
                        .toList();
                assertEquals(List.of(firstRun, secondRun), starts);
            }
        }

        /**
         * async-profiler writes {@code %t} in local time, so the machine's UTC offset when the
         * session starts travels in the recording's name: winter and summer in Prague differ.
         */
        @Test
        void theRecordingNameCarriesTheMachinesOffset() throws Exception {
            ZoneId prague = ZoneId.of("Europe/Prague");

            new InitExecutor(Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), prague)).execute(config());
            String winter = argFileOptions().getFirst();
            assertTrue(winter.endsWith("/profile-%t+0100.jfr"), () -> "the winter offset must be in the file option: " + winter);

            new InitExecutor(Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), prague)).execute(config());
            String summer = argFileOptions().getFirst();
            assertTrue(summer.endsWith("/profile-%t+0200.jfr"), () -> "the summer offset must be in the file option: " + summer);
        }
    }
}
