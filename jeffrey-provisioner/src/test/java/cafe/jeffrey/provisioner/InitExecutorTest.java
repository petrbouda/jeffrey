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

import cafe.jeffrey.provisioner.feature.JvmFeature;
import cafe.jeffrey.shared.common.HeartbeatConstants;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
        void createsHeartbeatDirectoryWithoutALiveRepository() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config());

            assertFalse(Files.exists(sessionPath().resolve("streaming-repo")));
            assertTrue(Files.isDirectory(sessionPath().resolve(HeartbeatConstants.HEARTBEAT_DIR)));
        }

        @Test
        void writesTheThreeMarkerFiles() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config());

            Path project = workspacesDir.resolve(WORKSPACE_REF_ID).resolve(PROJECT_NAME);
            assertTrue(Files.exists(project.resolve(JeffreyLayout.PROJECT_INFO_FILE)), "project marker");
            assertTrue(Files.exists(project.resolve(INSTANCE_NAME).resolve(JeffreyLayout.INSTANCE_INFO_FILE)),
                    "instance marker");
            assertTrue(Files.exists(sessionPath().resolve(JeffreyLayout.SESSION_INFO_FILE)), "session marker");
        }

        @Test
        void leavesNoTemporaryMarkerFilesBehind() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config());

            try (Stream<Path> tree = Files.walk(workspacesDir)) {
                List<Path> temps = tree.filter(path -> path.getFileName().toString().endsWith(".tmp")).toList();
                assertEquals(List.of(), temps, "atomic writes must not leave .tmp files");
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
                    + "loop=15m,chunksize=5m,file=" + session + "/profile-%t.jfr", argFileOptions().getFirst());
        }

        /** A missing library would stop the JVM, so the session runs unprofiled instead. */
        @Test
        void argFileLeavesTheProfilerOutWhenTheProfilerPathLibraryIsMissing() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config(tempDir.resolve("missing.so")));

            List<String> options = argFileOptions();
            assertFalse(options.isEmpty(), "the other features are still written");
            assertTrue(options.stream().noneMatch(option -> option.startsWith("-agentpath:")),
                    () -> "the profiler must not be armed: " + options);
            assertTrue(Files.exists(sessionPath().resolve(JeffreyLayout.SESSION_INFO_FILE)),
                    "the session is still recorded");
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
                    "export JEFFREY_FILE_PATTERN=" + session + "/profile-%t.jfr",
                    // Where the files would go, not that anything will write them: whether to
                    // report is the application's decision
                    "export JEFFREY_HEARTBEAT_DIR=" + session + "/.heartbeat"),
                    layoutExports);
            assertTrue(Files.readString(envFile).endsWith("\n"), "env file must be newline-terminated");
        }
    }

    @Nested
    class JfrEvents {

        private Path eventsJfc() throws IOException {
            return sessionPath().resolve(JvmFeature.JfrEvents.FILE);
        }

        @Test
        void startsTheRecordingFromTheSessionsOwnConfiguration() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config());

            List<String> options = argFileOptions();
            assertTrue(options.contains("-XX:StartFlightRecording:name=jeffrey-events,"
                    + "maxage=30m,settings=" + eventsJfc()), () -> "argfile: " + options);
            assertTrue(Files.readString(eventsJfc()).contains("<event name=\"jdk.ThreadPark\">"));
        }

        /** The deployment's own events go into the same file, after the tracing thresholds. */
        @Test
        void addsTheDeploymentsEvents() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config(Map.of(
                    "JEFFREY_JFR_ADD_EVENT_SETTINGS", "jdk.JavaExceptionThrow#stackTrace=true")));

            String jfc = Files.readString(eventsJfc());
            assertTrue(jfc.contains("<event name=\"jdk.ThreadPark\">"), jfc);
            assertTrue(jfc.contains("<event name=\"jdk.JavaExceptionThrow\">"), jfc);
        }

        @Test
        void recordsTheDeploymentsEventsWithTracingOff() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config(Map.of(
                    "JEFFREY_TRACING_ENABLED", "false",
                    "JEFFREY_JFR_ADD_EVENT_SETTINGS", "jdk.JavaExceptionThrow#stackTrace=true")));

            String jfc = Files.readString(eventsJfc());
            assertFalse(jfc.contains("jdk.ThreadPark"), jfc);
            assertTrue(jfc.contains("<event name=\"jdk.JavaExceptionThrow\">"), jfc);
        }

        @Test
        void writesNothingWhenTracingIsOff() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config(Map.of("JEFFREY_TRACING_ENABLED", "false")));

            assertFalse(Files.exists(eventsJfc()));
            assertTrue(argFileOptions().stream().noneMatch(option -> option.contains("StartFlightRecording")));
        }

        /** Unquoted, the launcher would read the # as a comment and cut the profiler's options short. */
        @Test
        void quotesAnEventSettingInTheArgFile() throws Exception {
            String command = "start,event=cpu,jfrsync=+jdk.JavaExceptionThrow#stackTrace=true,file=out.jfr";
            new InitExecutor(Clock.systemUTC()).execute(config(Map.of("JEFFREY_PROFILER_COMMAND", command)));

            String agentLine = argFileOptions().getFirst();
            assertTrue(agentLine.startsWith("\"-agentpath:") && agentLine.endsWith(command + "\""), agentLine);
        }
    }

    @Nested
    class JeffreyAgent {

        private Path agentJar;

        /** A run whose jeffrey-agent.path jar is in place, as jeffrey-jib bakes it. */
        private InitConfig config(String agentEnabled) throws IOException {
            agentJar = tempDir.resolve("jeffrey-agent.jar");
            if (Files.notExists(agentJar)) {
                Files.createFile(agentJar);
            }
            return config(agentEnabled, agentJar);
        }

        private InitConfig config(String agentEnabled, Path agentPath) throws IOException {
            return InitExecutorTest.this.config(Map.of(
                    "JEFFREY_AGENT_ENABLED", agentEnabled,
                    "JEFFREY_AGENT_PATH", agentPath.toString()));
        }

        private List<String> agentOptions() throws IOException {
            return argFileOptions().stream().filter(option -> option.startsWith("-javaagent:")).toList();
        }

        private List<String> sessionFiles() throws IOException {
            try (Stream<Path> files = Files.list(sessionPath())) {
                return files.map(path -> path.getFileName().toString()).toList();
            }
        }

        /** Loaded from where the image keeps it, so nothing is copied into the session. */
        @Test
        void attachesTheAgentFromTheAgentPath() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config("true"));

            assertEquals(List.of("-javaagent:" + agentJar), agentOptions());
            List<String> sessionFiles = sessionFiles();
            assertTrue(sessionFiles.stream().noneMatch(name -> name.endsWith(".jar")),
                    () -> "no jar is written into the session: " + sessionFiles);
        }

        /** The agent reads the same directory the library would, so the argfile still names it. */
        @Test
        void keepsNamingTheHeartbeatDirectory() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config("true"));

            assertTrue(argFileOptions().contains("-D" + HeartbeatConstants.DIRECTORY_PROPERTY + "="
                    + sessionPath().resolve(HeartbeatConstants.HEARTBEAT_DIR)));
        }

        /** Switched off, liveness is left to the jeffrey-heartbeat library the application carries. */
        @Test
        void leavesTheAgentOutWhenSwitchedOff() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config("false"));

            assertEquals(List.of(), agentOptions());
            assertTrue(argFileOptions().contains("-D" + HeartbeatConstants.DIRECTORY_PROPERTY + "="
                    + sessionPath().resolve(HeartbeatConstants.HEARTBEAT_DIR)), "the library still needs the directory");
        }

        /** A missing jar would stop the JVM, so the session runs without the agent instead. */
        @Test
        void provisionsWithoutTheAgentWhenTheJarIsMissing() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config("true", tempDir.resolve("missing.jar")));

            assertEquals(List.of(), agentOptions());
            assertTrue(Files.exists(sessionPath().resolve(JeffreyLayout.SESSION_INFO_FILE)), "the session is still recorded");
        }
    }

    @Nested
    class RepeatedRuns {

        /** A second run reuses the project and instance and adds a session of its own. */
        @Test
        void secondRunAddsASessionAndKeepsTheProject() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config());
            String firstProjectMarker = Files.readString(
                    workspacesDir.resolve(WORKSPACE_REF_ID).resolve(PROJECT_NAME).resolve(JeffreyLayout.PROJECT_INFO_FILE));

            new InitExecutor(Clock.systemUTC()).execute(config());

            Path instancePath = workspacesDir.resolve(WORKSPACE_REF_ID).resolve(PROJECT_NAME).resolve(INSTANCE_NAME);
            try (Stream<Path> sessions = Files.list(instancePath)) {
                assertEquals(2, sessions.filter(Files::isDirectory).count(), "one session per run");
            }

            assertEquals(firstProjectMarker, Files.readString(
                    workspacesDir.resolve(WORKSPACE_REF_ID).resolve(PROJECT_NAME).resolve(JeffreyLayout.PROJECT_INFO_FILE)),
                    "the project marker must not be rewritten");
        }

        /** A session's place in its instance is its start time; no ordinal is written down. */
        @Test
        void eachSessionRecordsWhenItsRunStarted() throws Exception {
            Instant firstRun = Instant.parse("2026-03-01T10:00:00Z");
            Instant secondRun = Instant.parse("2026-03-01T11:00:00Z");
            new InitExecutor(Clock.fixed(firstRun, ZoneOffset.UTC)).execute(config());
            new InitExecutor(Clock.fixed(secondRun, ZoneOffset.UTC)).execute(config());

            Path instancePath = workspacesDir.resolve(WORKSPACE_REF_ID).resolve(PROJECT_NAME).resolve(INSTANCE_NAME);
            try (Stream<Path> sessions = Files.list(instancePath)) {
                Set<Long> startTimes = sessions
                        .filter(Files::isDirectory)
                        .map(session -> session.resolve(JeffreyLayout.SESSION_INFO_FILE))
                        .map(InitExecutorTest::readSessionMarker)
                        .map(RemoteProjectInstanceSession::createdAt)
                        .collect(Collectors.toSet());
                assertEquals(Set.of(firstRun.toEpochMilli(), secondRun.toEpochMilli()), startTimes);
            }
        }
    }

    private static RemoteProjectInstanceSession readSessionMarker(Path sessionInfoFile) {
        try {
            return Json.read(Files.readString(sessionInfoFile), RemoteProjectInstanceSession.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
