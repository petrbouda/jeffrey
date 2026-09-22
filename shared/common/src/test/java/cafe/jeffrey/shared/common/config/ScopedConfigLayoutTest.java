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


package cafe.jeffrey.shared.common.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopedConfigLayoutTest {

    private static final Path SCOPE_DIR = Path.of("/volume/workspaces/uat");

    @Nested
    class Paths {

        @Test
        void configFileSitsInTheScopesConfigDirectory() {
            assertEquals(
                    Path.of("/volume/workspaces/uat/.config/jeffrey.conf"),
                    ScopedConfigLayout.configFile(SCOPE_DIR));
        }

        @Test
        void temporaryFileIsASiblingOfTheDocument() {
            Path configFile = ScopedConfigLayout.configFile(SCOPE_DIR);
            Path temporary = ScopedConfigLayout.temporaryFile(configFile, "abc");

            assertEquals(configFile.getParent(), temporary.getParent(),
                    "a rename is only atomic inside one directory");
        }

        @Test
        void temporaryFileIsUniquePerToken() {
            Path configFile = ScopedConfigLayout.configFile(SCOPE_DIR);

            assertNotEquals(
                    ScopedConfigLayout.temporaryFile(configFile, "one"),
                    ScopedConfigLayout.temporaryFile(configFile, "two"));
        }

        @Test
        void temporaryFileRejectsABlankToken() {
            Path configFile = ScopedConfigLayout.configFile(SCOPE_DIR);

            assertThrows(IllegalArgumentException.class,
                    () -> ScopedConfigLayout.temporaryFile(configFile, " "));
        }

        @Test
        void temporaryFileIsNotMistakenForADocument() {
            Path temporary = ScopedConfigLayout.temporaryFile(
                    ScopedConfigLayout.configFile(SCOPE_DIR), "abc");

            assertFalse(temporary.getFileName().toString().endsWith(".conf"),
                    "a reader filtering on .conf must never pick up a half-written file");
        }
    }

    @Nested
    class PublishablePaths {

        @Test
        void aTypesPathIsItsNameLowerCasedWithDashes() {
            assertEquals("asprof-settings", ScopedConfigLayout.hoconPath(ConfigType.ASPROF_SETTINGS));
        }

        @Test
        void everyTypeHasAPath() {
            Set<String> paths = Arrays.stream(ConfigType.values())
                    .map(ScopedConfigLayout::hoconPath)
                    .collect(Collectors.toSet());

            assertEquals(ConfigType.values().length, paths.size(), "two types share a path");
        }

        @Test
        void publishablePathsAreExactlyTheTypesPaths() {
            assertEquals(
                    Arrays.stream(ConfigType.values())
                            .map(ScopedConfigLayout::hoconPath)
                            .collect(Collectors.toSet()),
                    ScopedConfigLayout.publishablePaths());
        }

        /**
         * The no-mapping design holds only while every key is top level: a dotted path could not be
         * derived from an enum name, and adding one would quietly reintroduce a lookup table.
         */
        @Test
        void noPathIsNested() {
            for (String path : ScopedConfigLayout.publishablePaths()) {
                assertFalse(path.contains("."), "a published key must be top level: " + path);
            }
        }

        @Test
        void publishablePathsCannotBeModified() {
            assertThrows(UnsupportedOperationException.class,
                    () -> ScopedConfigLayout.publishablePaths().add("additional-jvm-options"));
        }
    }

    @Nested
    class Catalogue {

        /**
         * Pinned deliberately. A published value becomes a JVM option inside an application the hub
         * does not own, so the catalogue is a security boundary: widening it must fail here and be
         * argued for, never arrive as a side effect of another change.
         */
        @Test
        void theCatalogueHoldsOnlyTheReviewedTypes() {
            assertEquals(Set.of(ConfigType.ASPROF_SETTINGS), Set.of(ConfigType.values()));
        }

        @Test
        void scopesAreOrderedFromLeastToMostSpecific() {
            assertTrue(ConfigScope.GLOBAL.ordinal() < ConfigScope.WORKSPACE.ordinal());
            assertTrue(ConfigScope.WORKSPACE.ordinal() < ConfigScope.PROJECT.ordinal());
        }
    }
}
