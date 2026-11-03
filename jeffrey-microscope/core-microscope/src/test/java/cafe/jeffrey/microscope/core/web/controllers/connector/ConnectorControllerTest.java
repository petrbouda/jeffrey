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

package cafe.jeffrey.microscope.core.web.controllers.connector;

import cafe.jeffrey.microscope.model.connector.AzureFilesAddress;
import cafe.jeffrey.microscope.model.connector.AzureFilesAuth;
import cafe.jeffrey.microscope.model.connector.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.connector.ConnectorInfo;
import cafe.jeffrey.microscope.model.connector.ConnectorKind;
import cafe.jeffrey.microscope.model.connector.LocalDirectoryAddress;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorConnection;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorManager;
import cafe.jeffrey.microscope.core.web.dto.response.ConnectorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorControllerTest {

    private static final List<Class<? extends Annotation>> WRITE_MAPPINGS =
            List.of(PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);

    /** Reading the declared connector never opens a connection to it. */
    private static final ConnectorConnection.Factory NEVER_CONNECTS = info -> {
        throw new AssertionError("The connector controller must not connect: " + info.name());
    };

    private static ConnectorController answering(ConnectorInfo connector) {
        return new ConnectorController(new ConnectorManager(Optional.ofNullable(connector), NEVER_CONNECTS));
    }

    @Nested
    @DisplayName("Reading the connector")
    class Current {

        @Test
        void nothingConfiguredReadsAsNoConnectorRatherThanAnError() {
            assertNull(answering(null).current().connector());
        }

        @Test
        void aWritableShareIsReportedByItsNameAndLocation() {
            ConnectorResponse response = answering(new ConnectorInfo("Production", new AzureFilesAddress(
                    "https://jeffreyprod.file.core.windows.net", "recordings", "",
                    AzureFilesProtocol.SMB, AzureFilesAuth.SAS, false))).current().connector();

            assertEquals("Production", response.name());
            assertEquals(ConnectorKind.AZURE_FILES, response.kind());
            assertEquals("https://jeffreyprod.file.core.windows.net/recordings", response.location());
            assertFalse(response.readOnly());
        }

        /** A share declared read-only says so, and is named by its endpoint, share and directory. */
        @Test
        void anAzureFilesShareIsReportedByItsLocation() {
            ConnectorResponse response = answering(new ConnectorInfo("Prod share", new AzureFilesAddress(
                    "https://jeffreyprod.file.core.windows.net", "recordings", "volume",
                    AzureFilesProtocol.SMB, AzureFilesAuth.SAS, true))).current().connector();

            assertEquals("https://jeffreyprod.file.core.windows.net/recordings/volume", response.location());
            assertTrue(response.readOnly());
        }

        /** A local directory is named by its absolute path, normalized. */
        @Test
        void aLocalDirectoryIsReportedByItsPath() {
            ConnectorResponse response = answering(new ConnectorInfo("Scratch", new LocalDirectoryAddress(
                    Path.of("/tmp/jeffrey-volume/../jeffrey-volume"), true))).current().connector();

            assertEquals("Scratch", response.name());
            assertEquals(ConnectorKind.LOCAL, response.kind());
            assertEquals(Path.of("/tmp/jeffrey-volume").toString(), response.location());
            assertTrue(response.readOnly());
        }
    }

    /** Configuration is the only place the connector is declared, so nothing here may change it. */
    @Test
    void offersNoWayToConnectChangeOrDisconnectAConnector() {
        List<String> writes = Arrays.stream(ConnectorController.class.getDeclaredMethods())
                .filter(method -> WRITE_MAPPINGS.stream().anyMatch(method::isAnnotationPresent))
                .map(Method::getName)
                .toList();

        assertEquals(List.of(), writes);
    }
}
