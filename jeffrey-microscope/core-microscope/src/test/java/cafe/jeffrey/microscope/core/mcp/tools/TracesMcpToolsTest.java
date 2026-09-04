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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.profile.manager.TraceManager;
import cafe.jeffrey.profile.manager.model.trace.TraceNotificationGroupRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOverview;
import cafe.jeffrey.provider.profile.api.TraceNotificationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracesMcpToolsTest {

    /**
     * The tools build a UI link off the incoming request, the way ProfileMcpTools#link does.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }


    private static final TraceOverview TRACED = new TraceOverview(12, 340, 3, 5, 7, 2, 0, 0, 0, 0, 0, 8);
    private static final TraceOverview UNTRACED = new TraceOverview(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    @Mock
    ProfileManager profileManager;

    @Mock
    TraceManager traceManager;

    private TracesMcpTools tools() {
        when(profileManager.traceManager()).thenReturn(traceManager);
        // The answers carry a link to the matching UI view, which is built from the profile's id.
        when(profileManager.info()).thenReturn(new ProfileInfo(
                "p-1", "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        return new TracesMcpTools(profileManager);
    }

    private static TraceNotificationGroupRow poolPressure() {
        return new TraceNotificationGroupRow(
                "POOL_PRESSURE", "HIGH", "RESOURCE", "hikari", "Connection pool has no idle connections",
                4, 3, 60_012, 61_200, List.of("7f3a91", "7f3a92"));
    }

    @Nested
    class Notifications {

        @Test
        void returnsTheGroupsAsJson() {
            when(traceManager.notifications(any())).thenReturn(List.of(poolPressure()));

            String out = tools().notifications(null, null, null, null, null, null, null, null, null);

            assertTrue(out.contains("\"type\":\"POOL_PRESSURE\""), out);
            assertTrue(out.contains("\"exemplarTraceIds\":[\"7f3a91\",\"7f3a92\"]"), out);
        }

        @Test
        void passesEveryFilterThrough() {
            when(traceManager.notifications(any())).thenReturn(List.of(poolPressure()));

            tools().notifications("HIGH", "POOL_PRESSURE", "RESOURCE", "hikari", "idle",
                    "GET /orders", "SERVER", "jeffrey.HttpServerExchange", 5);

            ArgumentCaptor<TraceNotificationListQuery> query = ArgumentCaptor.forClass(TraceNotificationListQuery.class);
            verify(traceManager).notifications(query.capture());
            assertEquals("HIGH", query.getValue().severity());
            assertEquals("POOL_PRESSURE", query.getValue().type());
            assertEquals("RESOURCE", query.getValue().category());
            assertEquals("hikari", query.getValue().source());
            assertEquals("idle", query.getValue().messageContains());
            assertEquals(new TraceOperationId("GET /orders", "SERVER", "jeffrey.HttpServerExchange"),
                    query.getValue().operation());
            assertEquals(5, query.getValue().limit());
        }

        @Test
        void leavesTheOperationOutWhenNoneWasGiven() {
            when(traceManager.notifications(any())).thenReturn(List.of(poolPressure()));

            tools().notifications(null, null, null, null, null, null, null, null, null);

            ArgumentCaptor<TraceNotificationListQuery> query = ArgumentCaptor.forClass(TraceNotificationListQuery.class);
            verify(traceManager).notifications(query.capture());
            assertNull(query.getValue().operation());
            assertEquals(50, query.getValue().limit(), "the default limit applies");
        }

        @Test
        void refusesHalfAnOperation() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> tools().notifications(null, null, null, null, null, "GET /orders", null, null, null));

            assertTrue(e.getMessage().contains("all three"), e.getMessage());
        }

        @Test
        void tellsAnUntracedProfileFromOneWithoutNotifications() {
            when(traceManager.notifications(any())).thenReturn(List.of());

            when(traceManager.overview()).thenReturn(UNTRACED);
            assertTrue(tools().notifications(null, null, null, null, null, null, null, null, null)
                    .contains("contains no traces"));

            when(traceManager.overview()).thenReturn(TRACED);
            assertTrue(tools().notifications(null, null, null, null, null, null, null, null, null)
                    .contains("raised no notifications"));
        }

        @Test
        void saysWhenAFilterMatchedNothing() {
            when(traceManager.notifications(any())).thenReturn(List.of());

            String out = tools().notifications("CRITICAL", null, null, null, null, null, null, null, null);

            assertTrue(out.contains("No notification matches"), out);
        }
    }

    @Nested
    class Overview {

        @Test
        void carriesTheNotificationTotals() {
            when(traceManager.overview()).thenReturn(TRACED);

            String out = tools().overview();

            assertTrue(out.contains("\"notificationCount\":7"), out);
            assertTrue(out.contains("\"urgentNotificationCount\":2"), out);
        }
    }
}
