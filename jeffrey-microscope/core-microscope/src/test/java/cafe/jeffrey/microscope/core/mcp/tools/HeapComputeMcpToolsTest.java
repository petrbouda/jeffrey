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
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HeapComputeMcpToolsTest {

    private static final String PROFILE_ID = "profile-1";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    ProfileManager profileManager;

    @Mock
    HeapDumpManager heapDumpManager;

    private HeapDumpInitService initService;

    @BeforeEach
    void setUp() {
        initService = new HeapDumpInitService(CLOCK);
        when(profileManager.info()).thenReturn(profileInfo());
        when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
        // UiLinks builds from the current servlet request, so the tools need one bound.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private HeapComputeMcpTools tools() {
        return new HeapComputeMcpTools(profileManager, initService);
    }

    @Nested
    class Prepare {

        /**
         * A JFR recording asked to prepare a heap dump must be told it asked the wrong family, rather
         * than starting a run that fails on its first stage and reads like a broken dump.
         */
        @Test
        void refusesAProfileWithNoHeapDump() {
            when(heapDumpManager.heapDumpExists()).thenReturn(false);

            IllegalArgumentException e =
                    assertThrows(IllegalArgumentException.class, () -> tools().prepare(null));

            assertTrue(e.getMessage().contains("no heap dump"));
            assertTrue(e.getMessage().contains("profiles_features"));
        }

        @Test
        void refusesAnUnknownReportNamingTheOnesItHas() {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> tools().prepare("nonsense"));

            assertTrue(e.getMessage().contains("Unknown report"));
            assertTrue(e.getMessage().contains("leaks"));
        }

        /**
         * Returning immediately is the point: a dominator build over a large heap runs for minutes,
         * well past the point where a client abandons the call.
         */
        @Test
        void returnsTheStageListWithoutWaitingForTheWork() {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);

            String result = tools().prepare(null);

            assertTrue(result.contains("\"stages\""));
            assertTrue(result.contains("dominator"));
            assertTrue(result.contains("heap_status"));
        }
    }

    @Nested
    class Status {

        @Test
        void reportsAnIdleProfileRatherThanFailing() {
            String result = tools().status();

            assertTrue(result.contains("\"running\":false"));
            assertTrue(result.contains("\"stages\""));
        }
    }

    private static ProfileInfo profileInfo() {
        return new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Heap dump", RecordingEventSource.HEAP_DUMP,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1");
    }
}
