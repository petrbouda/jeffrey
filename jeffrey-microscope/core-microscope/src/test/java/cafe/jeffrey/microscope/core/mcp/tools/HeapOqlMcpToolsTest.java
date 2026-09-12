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

import cafe.jeffrey.profile.mcp.ToolExecutionException;
import cafe.jeffrey.profile.heapdump.model.OQLQueryRequest;
import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.model.OQLResultEntry;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HeapOqlMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String QUERY = "SELECT * FROM INSTANCEOF java.util.HashMap";
    private static final String HASH_MAP = "java.util.HashMap";

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    @Mock
    ProfileManager profileManager;

    @Mock
    HeapDumpManager heapDumpManager;

    /**
     * The answer carries a link into the OQL page, which UiLinks builds off the request being served.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.HEAP_DUMP,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private HeapOqlMcpTools tools() {
        return new HeapOqlMcpTools(profileManager);
    }

    private void answers(OQLQueryResult result) {
        when(heapDumpManager.executeQuery(any())).thenReturn(result);
    }

    private static OQLQueryResult twoInstances() {
        return OQLQueryResult.success(
                List.of(
                        OQLResultEntry.ofInstance(4711L, HASH_MAP, "{size=3}", 48),
                        OQLResultEntry.ofInstance(4712L, HASH_MAP, "{size=9}", 48)),
                2, false, 17);
    }

    private OQLQueryRequest capturedRequest() {
        ArgumentCaptor<OQLQueryRequest> request = ArgumentCaptor.forClass(OQLQueryRequest.class);
        verify(heapDumpManager).executeQuery(request.capture());
        return request.getValue();
    }

    @Nested
    class Oql {

        @Test
        void returnsTheRowsWithTheObjectIdsTheFollowUpToolsTake() {
            answers(twoInstances());

            String out = tools().oql(QUERY, null, null);

            assertTrue(out.contains("\"objectId\":4711"), out);
            assertTrue(out.contains("\"className\":\"" + HASH_MAP + "\""), out);
            assertTrue(out.contains("\"totalCount\":2"), out);
            assertTrue(out.contains("\"executionTimeMs\":17"), out);
        }

        /**
         * A row that is a computed value rather than an object has nothing to inspect further, and the
         * null objectId is what says so - dropping the field would read as an object without an id.
         */
        @Test
        void keepsTheNullObjectIdOfAComputedRow() {
            answers(OQLQueryResult.success(List.of(OQLResultEntry.ofValue("42")), 1, false, 3));

            assertTrue(tools().oql(QUERY, null, null).contains("\"objectId\":null"));
        }

        @Test
        void carriesTheQueryIntoTheLinkToTheOqlPage() {
            answers(twoInstances());

            String out = tools().oql(QUERY, null, null);

            assertTrue(out.contains("/profiles/" + PROFILE_ID + "/oql"), out);
            assertTrue(out.contains("query="), out);
        }

        /**
         * The engine reports a parse failure in the result rather than by throwing, and the message
         * names the position - which is what lets the model correct its own query rather than retry it.
         */
        @Test
        void handsBackTheEnginesOwnParseFailure() {
            answers(OQLQueryResult.error("Unexpected token at position 14", 2));

            ToolExecutionException error = assertThrows(
                    ToolExecutionException.class,
                    () -> tools().oql("SELECT * FRM x", null, null));

            assertTrue(error.getMessage().contains("position 14"), error.getMessage());
            assertFalse(error.getMessage().contains("\"rows\""), error.getMessage());
        }
    }

    @Nested
    class Arguments {

        @Test
        void refusesAMissingQueryWithoutTouchingTheDump() {
            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class, () -> tools().oql(null, null, null));

            assertTrue(thrown.getMessage().contains("query is required"), thrown.getMessage());
            verify(heapDumpManager, never()).executeQuery(any());
        }

        @Test
        void refusesABlankQueryTheSameWay() {
            assertThrows(IllegalArgumentException.class, () -> tools().oql("   ", null, null));
        }

        @Test
        void trimsTheQueryBeforeRunningIt() {
            answers(twoInstances());

            tools().oql("  " + QUERY + "  ", null, null);

            assertEquals(QUERY, capturedRequest().query());
        }

        @Test
        void appliesTheDefaultLimitWhenNoneIsGiven() {
            answers(twoInstances());

            tools().oql(QUERY, null, null);

            assertEquals(DEFAULT_LIMIT, capturedRequest().limit());
        }

        /**
         * An OQL row is an object a reader then asks a follow-up about, so a request for everything is
         * a request for the reader's own context to be spent on one answer.
         */
        @Test
        void boundsALimitAboveTheCeiling() {
            answers(twoInstances());

            tools().oql(QUERY, 5_000, null);

            assertEquals(MAX_LIMIT, capturedRequest().limit());
        }

        /**
         * Retained size builds the dominator tree first, which on a large dump takes minutes, so it is
         * opted into rather than paid for by accident.
         */
        @Test
        void leavesRetainedSizeOffUnlessItWasAskedFor() {
            answers(twoInstances());

            tools().oql(QUERY, null, null);

            assertFalse(capturedRequest().includeRetainedSize());
        }

        @Test
        void computesRetainedSizeWhenItWasAskedFor() {
            answers(twoInstances());

            tools().oql(QUERY, null, true);

            assertTrue(capturedRequest().includeRetainedSize());
        }
    }
}
