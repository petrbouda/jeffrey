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

package cafe.jeffrey.microscope.core.manager.ide;

import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginClient.PluginInstance;
import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginClient.PluginNavigateResult;
import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginClient.PluginProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JeffreyPluginBridgeTest {

    private static final int PORT_START = 63342;
    private static final int PORT_END = 63344;
    private static final String PROFILE = "p1";
    private static final String PROJECT_ID = "loc-hash-1";
    private static final String PROJECT_NAME = "jeffrey";
    private static final String IDE_NAME = "IntelliJ IDEA";
    private static final long PID = 9688L;

    private JeffreyPluginClient client;
    private IdeTargetCache cache;
    private JeffreyPluginBridge bridge;

    @BeforeEach
    void setUp() {
        client = mock(JeffreyPluginClient.class);
        cache = new IdeTargetCache();
        bridge = new JeffreyPluginBridge(new PortRange(PORT_START, PORT_END), client, cache);
        when(client.instance(anyInt())).thenReturn(Optional.empty());
    }

    private static IdeTarget target(int port) {
        return new IdeTarget(port, PROJECT_ID, IDE_NAME, PROJECT_NAME, PID);
    }

    private static PluginInstance instance(int port) {
        return new PluginInstance(1, "iid", IDE_NAME, "IC", "2024.3", PID, port, "now",
                List.of(new PluginProject(PROJECT_ID, PROJECT_NAME, "/path", true, true, "master")));
    }

    private static PluginNavigateResult resolved() {
        return new PluginNavigateResult(true, "src", "File.java", 1, false, false, false, null, null);
    }

    private static PluginNavigateResult unresolved() {
        return new PluginNavigateResult(false, null, null, null, false, false, false, null, "not found");
    }

    private static IdeOpenRequest openRequest() {
        return new IdeOpenRequest(PROFILE, "com.example.Foo", "Foo.bar", 1);
    }

    @Nested
    class Status {

        @Test
        void notLinkedWithoutDiscovery() {
            IdeTargetStatus status = bridge.targetStatus(PROFILE);

            assertTrue(status.selectable());
            assertFalse(status.linked());
            verify(client, never()).instance(anyInt());
        }

        @Test
        void linkedReflectsCacheWithoutDiscovery() {
            bridge.selectTarget(PROFILE, target(PORT_START));

            IdeTargetStatus status = bridge.targetStatus(PROFILE);

            assertTrue(status.linked());
            assertEquals(IDE_NAME, status.ideName());
            assertEquals(PROJECT_NAME, status.projectName());
            assertEquals(PORT_START, status.port());
            assertEquals(PID, status.pid());
            verify(client, never()).instance(anyInt());
        }
    }

    @Nested
    class Disconnect {

        @Test
        void clearsAnExistingLink() {
            bridge.selectTarget(PROFILE, target(PORT_START));

            assertTrue(bridge.clearTarget(PROFILE));
            assertFalse(bridge.targetStatus(PROFILE).linked());
        }

        @Test
        void returnsFalseWhenNothingLinked() {
            assertFalse(bridge.clearTarget(PROFILE));
        }
    }

    @Nested
    class Open {

        @Test
        void reportsNoTargetWhenNotLinked() {
            IdeOpenResult result = bridge.open(openRequest());

            assertFalse(result.success());
            assertEquals(IdeFailureReason.NO_TARGET, result.reason());
            verify(client, never()).navigate(anyInt(), any());
            verify(client, never()).instance(anyInt());
        }

        @Test
        void succeedsOnCachedPortWithoutDiscovery() {
            bridge.selectTarget(PROFILE, target(PORT_START));
            when(client.navigate(eq(PORT_START), any())).thenReturn(resolved());

            IdeOpenResult result = bridge.open(openRequest());

            assertTrue(result.success());
            verify(client, never()).instance(anyInt());
        }

        @Test
        void reachableButUnresolvedReportsNotResolved() {
            bridge.selectTarget(PROFILE, target(PORT_START));
            when(client.navigate(eq(PORT_START), any())).thenReturn(unresolved());

            IdeOpenResult result = bridge.open(openRequest());

            assertFalse(result.success());
            assertEquals(IdeFailureReason.NOT_RESOLVED, result.reason());
            verify(client, never()).instance(anyInt());
        }

        @Test
        void unreachableReresolvesToNewPortAndRetries() {
            bridge.selectTarget(PROFILE, target(PORT_START));
            when(client.navigate(eq(PORT_START), any())).thenReturn(null);
            when(client.instance(PORT_END)).thenReturn(Optional.of(instance(PORT_END)));
            when(client.navigate(eq(PORT_END), any())).thenReturn(resolved());

            IdeOpenResult result = bridge.open(openRequest());

            assertTrue(result.success());
            assertEquals(PORT_END, bridge.targetStatus(PROFILE).port());
        }

        @Test
        void unreachableAndGoneReportsUnavailable() {
            bridge.selectTarget(PROFILE, target(PORT_START));
            when(client.navigate(eq(PORT_START), any())).thenReturn(null);

            IdeOpenResult result = bridge.open(openRequest());

            assertFalse(result.success());
            assertEquals(IdeFailureReason.UNREACHABLE, result.reason());
        }
    }
}
