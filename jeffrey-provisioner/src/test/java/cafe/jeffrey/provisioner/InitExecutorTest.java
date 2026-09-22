/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.provisioner;

import cafe.jeffrey.shared.common.HeartbeatConstants;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.config.ConfigSource;
import cafe.jeffrey.shared.common.config.ContentDigest;
import cafe.jeffrey.shared.common.config.ScopedConfigLayout;
import cafe.jeffrey.shared.common.model.repository.AppliedConfigLayer;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
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

    private Path workspacesDir;
    private Path argFile;
    private Path envFile;

    private InitConfig config() {
        workspacesDir = tempDir.resolve("workspaces");
        argFile = tempDir.resolve("jvm.args");
        envFile = tempDir.resolve("jeffrey.env");

        Map<String, String> env = Map.of(
                "JEFFREY_WORKSPACES_DIR", workspacesDir.toString(),
                "JEFFREY_PROJECT_NAME", PROJECT_NAME,
                "JEFFREY_WORKSPACE_REF_ID", WORKSPACE_REF_ID,
                "JEFFREY_INSTANCE_NAME", INSTANCE_NAME,
                "JEFFREY_ARG_FILE", argFile.toString(),
                "JEFFREY_ENV_FILE", envFile.toString());

        return InitConfig.fromEnvironment(env::get);
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

    /** Writes a hub-published file into a scope's folder, creating the folder if needed. */
    private void publish(Path scopeDir, String content) throws IOException {
        Path file = ScopedConfigLayout.configFile(scopeDir);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private RemoteProjectInstanceSession sessionMarker() throws IOException {
        return Json.read(
                Files.readString(sessionPath().resolve(JeffreyLayout.SESSION_INFO_FILE)),
                RemoteProjectInstanceSession.class);
    }

    /**
     * End to end over the real filesystem: a file the hub would have written, read by a full run.
     * These pin the behaviour a user sees — what the JVM is started with and what the hub is told —
     * rather than the shape of the code that produces it.
     */
    @Nested
    class HubPublishedConfiguration {

        @Test
        void aWorkspaceFileSuppliesTheProfilerCommand() throws Exception {
            InitConfig config = config();
            publish(workspacesDir.resolve(WORKSPACE_REF_ID),
                    "asprof-settings = \"-agentpath:/opt/libasyncProfiler.so=start,cpu\"\n");

            new InitExecutor(Clock.systemUTC()).execute(config);

            assertTrue(Files.readString(argFile).contains("start,cpu"));
            assertEquals(ConfigSource.HUB_WORKSPACE.name(), sessionMarker().profilerCommandSource());
        }

        @Test
        void theMarkerRecordsEveryLayerWithTheDigestOfTheFileAsRead() throws Exception {
            InitConfig config = config();
            String workspaceFile = "asprof-settings = \"-agentpath:/opt/lib.so=start,cpu\"\n";
            publish(workspacesDir, "asprof-settings = \"-agentpath:/opt/lib.so=start,alloc\"\n");
            publish(workspacesDir.resolve(WORKSPACE_REF_ID), workspaceFile);

            new InitExecutor(Clock.systemUTC()).execute(config);

            List<AppliedConfigLayer> layers = sessionMarker().configLayers();
            assertEquals(List.of(ConfigScope.GLOBAL, ConfigScope.WORKSPACE),
                    layers.stream().map(AppliedConfigLayer::scope).toList());
            assertEquals(
                    ContentDigest.sha256Hex(workspaceFile.getBytes(StandardCharsets.UTF_8)),
                    layers.get(1).digest());
        }

        /**
         * The fail-open rule: configuration that cannot be read costs the defaults, never the run.
         */
        @Test
        void anUnreadableFileLeavesTheBuiltInCommandAndStillProvisions() throws Exception {
            InitConfig config = config();
            publish(workspacesDir.resolve(WORKSPACE_REF_ID), "{ not hocon at all");

            new InitExecutor(Clock.systemUTC()).execute(config);

            assertTrue(Files.exists(argFile), "a broken published file must not stop provisioning");
            assertEquals(ConfigSource.BUILT_IN.name(), sessionMarker().profilerCommandSource());
            assertTrue(sessionMarker().configLayers().isEmpty());
        }

        /**
         * A file on the volume is the one place an outsider could try to put arbitrary flags into
         * someone else's JVM. Whatever it says, only the catalogue's keys may reach the argfile.
         */
        @Test
        void aFileCannotSmuggleJvmOptionsIntoTheArgfile() throws Exception {
            InitConfig config = config();
            publish(workspacesDir.resolve(WORKSPACE_REF_ID), """
                    asprof-settings = "-agentpath:/opt/lib.so=start,cpu"
                    additional-jvm-options = "-XX:OnOutOfMemoryError=touch /tmp/pwned"
                    """);

            new InitExecutor(Clock.systemUTC()).execute(config);

            String argFileContent = Files.readString(argFile);
            assertTrue(argFileContent.contains("start,cpu"));
            assertFalse(argFileContent.contains("OnOutOfMemoryError"),
                    "a published file must not be able to add a JVM flag");
        }

        @Test
        void aProjectFileOverridesTheWorkspaceOne() throws Exception {
            InitConfig config = config();
            publish(workspacesDir.resolve(WORKSPACE_REF_ID),
                    "asprof-settings = \"-agentpath:/opt/lib.so=start,alloc\"\n");
            publish(workspacesDir.resolve(WORKSPACE_REF_ID).resolve(PROJECT_NAME),
                    "asprof-settings = \"-agentpath:/opt/lib.so=start,cpu\"\n");

            new InitExecutor(Clock.systemUTC()).execute(config);

            assertTrue(Files.readString(argFile).contains("start,cpu"));
            assertEquals(ConfigSource.HUB_PROJECT.name(), sessionMarker().profilerCommandSource());
        }
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

            List<String> options = Files.readString(argFile).lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .toList();

            assertTrue(options.stream().allMatch(option -> option.startsWith("-")),
                    () -> "every line must be a JVM option: " + options);
            assertTrue(options.stream().anyMatch(option -> option.startsWith("-agentpath:")),
                    () -> "the profiler must be armed: " + options);
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
            List<String> layoutExports = normalized(Files.readString(envFile)).lines()
                    .filter(line -> !line.contains("JEFFREY_PROFILER_SETTINGS"))
                    .toList();

            assertEquals(List.of(
                    "export JEFFREY_WORKSPACES=" + workspacesDir,
                    "export JEFFREY_CURRENT_WORKSPACE=" + workspacesDir.resolve(WORKSPACE_REF_ID),
                    "export JEFFREY_CURRENT_PROJECT="
                            + workspacesDir.resolve(WORKSPACE_REF_ID).resolve(PROJECT_NAME),
                    "export JEFFREY_CURRENT_SESSION=" + session,
                    "export JEFFREY_FILE_PATTERN=" + session + "/profile-%t.jfr",
                    "export JEFFREY_HEARTBEAT_DIR=" + session + "/.heartbeat",
                    // The directory is exported either way — it says where the files would go,
                    // not that anything will write them. This fixture declares no liveness, which
                    // is the default: whether the application carries jeffrey-heartbeat is a
                    // build-time fact the provisioner cannot see and must not assume
                    "export JEFFREY_HEARTBEAT_ENABLED=false"),
                    layoutExports);
            assertTrue(Files.readString(envFile).endsWith("\n"), "env file must be newline-terminated");
        }
    }

    @Nested
    class RepeatedRuns {

        /** A second run reuses the project and instance and adds a session ordered after the first. */
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

        @Test
        void sessionOrderIncrementsPerRun() throws Exception {
            new InitExecutor(Clock.systemUTC()).execute(config());
            new InitExecutor(Clock.systemUTC()).execute(config());

            Path instancePath = workspacesDir.resolve(WORKSPACE_REF_ID).resolve(PROJECT_NAME).resolve(INSTANCE_NAME);
            try (Stream<Path> sessions = Files.list(instancePath)) {
                List<String> orders = sessions
                        .filter(Files::isDirectory)
                        .map(session -> session.resolve(JeffreyLayout.SESSION_INFO_FILE))
                        .map(InitExecutorTest::readOrder)
                        .sorted()
                        .toList();
                assertEquals(List.of("1", "2"), orders);
            }
        }
    }

    private static String readOrder(Path sessionInfoFile) {
        try {
            String content = Files.readString(sessionInfoFile);
            int index = content.indexOf("\"order\":");
            return content.substring(index + "\"order\":".length()).split("[,}]")[0].trim();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
