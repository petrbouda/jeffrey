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

package cafe.jeffrey.microscope.core.manager.remote;

import cafe.jeffrey.microscope.model.remote.HubAddress;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RemoteManagerTest {

    private static final RemoteInfo PRODUCTION =
            new RemoteInfo("Production", new HubAddress("hub.example.com", 443, false));

    @Nested
    class NothingConfigured {

        private final RemoteManager manager = new RemoteManager(Optional.empty(), remote -> {
            throw new AssertionError("nothing is configured, so nothing may be connected");
        });

        @Test
        void hasNoRemote() {
            assertTrue(manager.current().isEmpty());
            assertTrue(manager.info().isEmpty());
        }

        /** The refusal names the configuration, the only place a remote can be declared. */
        @Test
        void requireIsNotFoundAndSaysWhereToDeclareOne() {
            JeffreyClientException e = assertThrows(JeffreyClientException.class, manager::require);

            assertTrue(e.getCode().isNotFound());
            assertTrue(e.getMessage().contains("jeffrey.microscope.remote."), e.getMessage());
        }
    }

    @Nested
    class Configured {

        private final RemoteConnection connection = mock(RemoteConnection.class);

        @Test
        void connectsTheConfiguredRemote() {
            RemoteManager manager = new RemoteManager(Optional.of(PRODUCTION), remote -> {
                assertSame(PRODUCTION, remote);
                return connection;
            });

            assertSame(connection, manager.require());
            assertSame(connection, manager.current().orElseThrow());
        }

        /** Reading what is configured never opens a connection to it. */
        @Test
        void infoNeedsNoConnection() {
            RemoteManager manager = new RemoteManager(Optional.of(PRODUCTION), remote -> {
                throw new AssertionError("info() must not connect");
            });

            assertEquals(Optional.of(PRODUCTION), manager.info());
        }
    }
}
