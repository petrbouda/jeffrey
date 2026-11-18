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

package cafe.jeffrey.microscope.connector.spi;

import cafe.jeffrey.microscope.connector.local.LocalConnectorProvider;
import cafe.jeffrey.microscope.connector.local.LocalDirectoryAddress;
import cafe.jeffrey.microscope.model.connector.ConnectorKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorProvidersTest {

    /** A kind that claims a block already taken, as a second module bringing "local" would. */
    private static ConnectorProvider<Object> claiming(ConnectorKind kind) {
        return new ConnectorProvider<>() {
            @Override
            public ConnectorKind kind() {
                return kind;
            }

            @Override
            public Class<Object> settingsType() {
                return Object.class;
            }

            @Override
            public DeclaredConnector declare(Object settings) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /** This module provides the local kind itself, declared with {@code provides} in its module-info. */
    @Test
    void findsTheLocalKindThisModuleProvides() {
        assertTrue(ConnectorProviders.load().stream()
                .anyMatch(provider -> provider.kind().equals(LocalDirectoryAddress.KIND)));
    }

    @Test
    void ordersTheKindsByTheirBlock() {
        ConnectorProvider<Object> zeta = claiming(new ConnectorKind("ZETA", "zeta"));
        ConnectorProvider<Object> alpha = claiming(new ConnectorKind("ALPHA", "alpha"));

        assertEquals(List.of(alpha, zeta), ConnectorProviders.of(List.of(zeta, alpha)));
    }

    @Test
    void refusesTwoKindsClaimingOneBlock() {
        List<ConnectorProvider<?>> twice = List.of(new LocalConnectorProvider(),
                claiming(new ConnectorKind("OTHER", LocalDirectoryAddress.KIND.block())));

        assertThrows(IllegalStateException.class, () -> ConnectorProviders.of(twice));
    }

    @Test
    void refusesTwoKindsClaimingOneName() {
        List<ConnectorProvider<?>> twice = List.of(new LocalConnectorProvider(),
                claiming(new ConnectorKind(LocalDirectoryAddress.KIND.name(), "elsewhere")));

        assertThrows(IllegalStateException.class, () -> ConnectorProviders.of(twice));
    }
}
