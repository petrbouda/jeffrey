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

package cafe.jeffrey.shared.ui.remote.controller;

import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.AzureFilesAuth;
import cafe.jeffrey.microscope.model.remote.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.remote.HubAddress;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.shared.ui.remote.dto.RemoteResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteControllerTest {

    private static final List<Class<? extends Annotation>> WRITE_MAPPINGS =
            List.of(PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);

    private static RemoteController answering(RemoteInfo remote) {
        return new RemoteController(() -> Optional.ofNullable(remote));
    }

    @Nested
    @DisplayName("Reading the remote")
    class Current {

        @Test
        void nothingConfiguredReadsAsNoRemoteRatherThanAnError() {
            assertNull(answering(null).current().remote());
        }

        @Test
        void aJeffreyHubIsReportedByItsHostAndPort() {
            RemoteResponse response = answering(new RemoteInfo("Production", new HubAddress("hub.example.com", 443)))
                    .current().remote();

            assertEquals("Production", response.name());
            assertEquals("GRPC", response.kind());
            assertEquals("hub.example.com:443", response.location());
            assertEquals(443, response.port());
            assertFalse(response.readOnly());
        }

        /** A share declared read-only says so, and is named by its endpoint, share and directory. */
        @Test
        void anAzureFilesShareIsReportedByItsLocation() {
            RemoteResponse response = answering(new RemoteInfo("Prod share", new AzureFilesAddress(
                    "https://jeffreyprod.file.core.windows.net", "recordings", "volume",
                    AzureFilesProtocol.SMB, AzureFilesAuth.SAS, true))).current().remote();

            assertEquals("AZURE_FILES", response.kind());
            assertEquals("https://jeffreyprod.file.core.windows.net/recordings/volume", response.location());
            assertTrue(response.readOnly());
            assertNull(response.hostname());
        }
    }

    /** Configuration is the only place the remote is declared, so nothing here may change it. */
    @Test
    void offersNoWayToConnectChangeOrDisconnectARemote() {
        List<String> writes = Arrays.stream(RemoteController.class.getDeclaredMethods())
                .filter(method -> WRITE_MAPPINGS.stream().anyMatch(method::isAnnotationPresent))
                .map(Method::getName)
                .toList();

        assertEquals(List.of(), writes);
    }
}
