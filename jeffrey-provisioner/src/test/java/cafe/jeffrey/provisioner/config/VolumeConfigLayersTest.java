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


package cafe.jeffrey.provisioner.config;

import cafe.jeffrey.provisioner.ProjectLayout;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.config.ScopedConfigLayout;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The published files belong to another process and may be absent, half-written or hand-edited, so
 * what matters most here is what <em>cannot</em> happen: no input makes discovery throw, and no
 * file can contribute a key the catalogue does not authorise.
 */
class VolumeConfigLayersTest {

    private static final String ASPROF_SETTINGS = "asprof-settings";

    @TempDir
    Path tempDir;

    @Nested
    class Discovery {

        @Test
        void findsNothingWhenNoFileExists() throws IOException {
            assertTrue(VolumeConfigLayers.discover(layout()).isEmpty());
        }

        @Test
        void findsEachScopeInMergeOrder() throws IOException {
            ProjectLayout layout = layout();
            publish(layout.workspaces(), "asprof-settings = \"global\"");
            publish(layout.workspace(), "asprof-settings = \"workspace\"");
            publish(layout.project(), "asprof-settings = \"project\"");

            List<VolumeConfigLayer> layers = VolumeConfigLayers.discover(layout);

            assertEquals(
                    List.of(ConfigScope.GLOBAL, ConfigScope.WORKSPACE, ConfigScope.PROJECT),
                    layers.stream().map(VolumeConfigLayer::scope).toList());
        }

        @Test
        void findsOnlyTheScopesThatHaveAFile() throws IOException {
            ProjectLayout layout = layout();
            publish(layout.workspace(), "asprof-settings = \"workspace\"");

            List<VolumeConfigLayer> layers = VolumeConfigLayers.discover(layout);

            assertEquals(1, layers.size());
            assertEquals(ConfigScope.WORKSPACE, layers.getFirst().scope());
        }

    }

    @Nested
    class BadInput {

        @Test
        void skipsAnUnparseableFileAndKeepsTheRest() throws IOException {
            ProjectLayout layout = layout();
            publish(layout.workspaces(), "asprof-settings = \"global\"");
            publish(layout.workspace(), "{ this is not hocon");
            publish(layout.project(), "asprof-settings = \"project\"");

            List<VolumeConfigLayer> layers = VolumeConfigLayers.discover(layout);

            assertEquals(
                    List.of(ConfigScope.GLOBAL, ConfigScope.PROJECT),
                    layers.stream().map(VolumeConfigLayer::scope).toList());
        }

        @Test
        void anEmptyFileContributesAnEmptyLayerRatherThanFailing() throws IOException {
            ProjectLayout layout = layout();
            publish(layout.workspace(), "");

            List<VolumeConfigLayer> layers = VolumeConfigLayers.discover(layout);

            assertEquals(1, layers.size());
            assertTrue(layers.getFirst().config().isEmpty());
        }

        @Test
        void ignoresADirectoryWhereTheFileShouldBe() throws IOException {
            ProjectLayout layout = layout();
            Files.createDirectories(ScopedConfigLayout.configFile(layout.workspace()));

            assertTrue(VolumeConfigLayers.discover(layout).isEmpty());
        }
    }

    @Nested
    class PublishableKeysOnly {

        /**
         * The file is the one thing an attacker reaching the hub, or anyone with write access to the
         * volume, could use to put arbitrary flags into somebody else's JVM. Dropping the keys is
         * what stops it, so this is the test that must never be relaxed.
         */
        @Test
        void dropsAKeyThatIsNotInTheCatalogue() throws IOException {
            ProjectLayout layout = layout();
            publish(layout.workspace(), """
                    asprof-settings = "start,cpu"
                    additional-jvm-options = "-XX:OnOutOfMemoryError=touch /tmp/pwned"
                    project { name = "stolen" }
                    """);

            VolumeConfigLayer layer = VolumeConfigLayers.discover(layout).getFirst();

            assertEquals(Set.of(ASPROF_SETTINGS), layer.config().root().keySet());
            assertEquals("start,cpu", layer.config().getString(ASPROF_SETTINGS));
        }

        @Test
        void aFileOfNothingButUnpublishableKeysBecomesAnEmptyLayer() throws IOException {
            ProjectLayout layout = layout();
            publish(layout.workspace(), "arg-file = \"/tmp/elsewhere\"");

            assertTrue(VolumeConfigLayers.discover(layout).getFirst().config().isEmpty());
        }

        /**
         * HOCON can read another file into itself. A published file is parsed inside the
         * application's own container, so honouring that would turn the hub into a way to read
         * arbitrary paths off it.
         */
        @Test
        void refusesToFollowAnIncludeDirective() throws IOException {
            ProjectLayout layout = layout();
            Path secret = tempDir.resolve("secret.conf");
            Files.writeString(secret, "asprof-settings = \"leaked\"\n");
            publish(layout.workspace(), "include file(\"" + secret.toAbsolutePath() + "\")\n");

            VolumeConfigLayer layer = VolumeConfigLayers.discover(layout).getFirst();

            assertFalse(layer.config().hasPath(ASPROF_SETTINGS), "an include must contribute nothing");
        }
    }

    private ProjectLayout layout() throws IOException {
        Path workspaces = Files.createDirectories(tempDir.resolve("workspaces"));
        Path workspace = Files.createDirectories(workspaces.resolve("uat"));
        Path project = Files.createDirectories(workspace.resolve("demo"));
        return new ProjectLayout(tempDir, workspaces, workspace, project);
    }

    private static void publish(Path scopeDir, String content) throws IOException {
        Path file = ScopedConfigLayout.configFile(scopeDir);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
