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

package cafe.jeffrey.microscope.connector.local;

import cafe.jeffrey.microscope.common.exception.ErrorCode;
import cafe.jeffrey.microscope.common.exception.JeffreyClientException;
import cafe.jeffrey.microscope.connector.api.ConnectorClients;
import cafe.jeffrey.microscope.connector.api.spi.ConnectorProviders;
import cafe.jeffrey.microscope.connector.api.spi.DeclaredConnector;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalConnectorProviderTest {

    /** A workspace that is not on the volume: deleting it is refused, for one reason or the other. */
    private static final String MISSING_WORKSPACE = "missing";

    private final LocalConnectorProvider provider = new LocalConnectorProvider();

    @TempDir
    Path root;

    /** This module provides the local kind, declared with {@code provides} and in {@code META-INF/services}. */
    @Test
    void isFoundAmongTheConnectorProviders() {
        assertTrue(ConnectorProviders.load().stream()
                .anyMatch(found -> found.kind().equals(LocalDirectoryAddress.KIND)));
    }

    @Nested
    class Declare {

        @Test
        void aDirectoryIsAWritableConnectorNamedByItsDirectory() {
            DeclaredConnector connector = provider.declare(new LocalSettings(root.toString(), false));

            assertEquals(new LocalDirectoryAddress(root, false), connector.address());
            assertEquals(root.getFileName().toString(), connector.defaultName());
        }

        @Test
        void keepsItsReadOnlyFlag() {
            assertTrue(provider.declare(new LocalSettings(root.toString(), true)).address().readOnly());
        }

        /** Not there yet is still a declaration: the connector reads as unreachable until it appears. */
        @Test
        void aDirectoryNotThereYetIsStillDeclared() {
            Path later = root.resolve("later");

            assertEquals(new LocalDirectoryAddress(later, false),
                    provider.declare(new LocalSettings(later.toString(), false)).address());
        }

        @Test
        void refusesABlankMissingOrRelativePath() {
            assertThrows(IllegalArgumentException.class, () -> provider.declare(new LocalSettings(" ", false)));
            assertThrows(IllegalArgumentException.class, () -> provider.declare(new LocalSettings(null, false)));
            assertThrows(IllegalArgumentException.class,
                    () -> provider.declare(new LocalSettings("relative/volume", false)));
        }
    }

    @Nested
    class Open {

        @Test
        void opensTheDirectoryAsClientsThatReportTheKind() {
            ConnectorClients clients = provider.declare(new LocalSettings(root.toString(), false)).open();

            assertEquals(LocalDirectoryAddress.KIND.block(), clients.discovery().info().version());
        }

        /** A writable volume looks the workspace up and finds nothing; a read-only one refuses first. */
        @Test
        void keepsItsReadOnlyFlag() {
            ConnectorClients writable = provider.declare(new LocalSettings(root.toString(), false)).open();
            ConnectorClients readOnly = provider.declare(new LocalSettings(root.toString(), true)).open();

            assertEquals(ErrorCode.WORKSPACE_NOT_FOUND, refusalOfDeleting(writable));
            assertEquals(ErrorCode.INVALID_REQUEST, refusalOfDeleting(readOnly));
        }

        private static ErrorCode refusalOfDeleting(ConnectorClients clients) {
            return assertThrows(JeffreyClientException.class,
                    () -> clients.discovery().deleteWorkspace(MISSING_WORKSPACE)).getCode();
        }
    }
}
