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

package cafe.jeffrey.microscope.core.manager.connector;

import cafe.jeffrey.microscope.model.connector.AzureFilesAddress;
import cafe.jeffrey.microscope.model.connector.AzureFilesAuth;
import cafe.jeffrey.microscope.model.connector.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.connector.ConnectorInfo;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ConnectorManagerTest {

    private static final ConnectorInfo PRODUCTION =
            new ConnectorInfo("Production", new AzureFilesAddress(
                    "https://jeffreyprod.file.core.windows.net", "recordings", "",
                    AzureFilesProtocol.SMB, AzureFilesAuth.SAS, false));

    @Nested
    class NothingConfigured {

        private final ConnectorManager manager = new ConnectorManager(Optional.empty(), connector -> {
            throw new AssertionError("nothing is configured, so nothing may be connected");
        });

        @Test
        void hasNoConnector() {
            assertTrue(manager.current().isEmpty());
            assertTrue(manager.info().isEmpty());
        }

        /** The refusal names the configuration, the only place a connector can be declared. */
        @Test
        void requireIsNotFoundAndSaysWhereToDeclareOne() {
            JeffreyClientException e = assertThrows(JeffreyClientException.class, manager::require);

            assertTrue(e.getCode().isNotFound());
            assertTrue(e.getMessage().contains("jeffrey.microscope.connector."), e.getMessage());
        }
    }

    @Nested
    class Configured {

        private final ConnectorConnection connection = mock(ConnectorConnection.class);

        @Test
        void connectsTheConfiguredConnector() {
            ConnectorManager manager = new ConnectorManager(Optional.of(PRODUCTION), connector -> {
                assertSame(PRODUCTION, connector);
                return connection;
            });

            assertSame(connection, manager.require());
            assertSame(connection, manager.current().orElseThrow());
        }

        /** Reading what is configured never opens a connection to it. */
        @Test
        void infoNeedsNoConnection() {
            ConnectorManager manager = new ConnectorManager(Optional.of(PRODUCTION), connector -> {
                throw new AssertionError("info() must not connect");
            });

            assertEquals(Optional.of(PRODUCTION), manager.info());
        }
    }
}
