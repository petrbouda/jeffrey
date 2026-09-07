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

import cafe.jeffrey.profile.common.treetable.EventViewerData;
import cafe.jeffrey.profile.manager.EventViewerManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.FieldDescription;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.Type;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventTypeMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String RECORDED_TYPE = EventTypeName.GARBAGE_COLLECTION;
    private static final String RECORDED_LABEL = "Garbage Collection";
    private static final String UNRECORDED_TYPE = "jdk.NoSuchEvent";
    private static final String SUM_OF_PAUSES_FIELD = "sumOfPauses";
    private static final String LONGEST_PAUSE_FIELD = "longestPause";
    private static final String EVENTS_VIEW_LINK = "/profiles/p-1/events";

    @Mock
    ProfileManager profileManager;

    @Mock
    EventViewerManager eventViewerManager;

    /**
     * The answer carries a link to the events view, and {@code UiLinks} reads the request bound to
     * the current thread to build it.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.eventViewerManager()).thenReturn(eventViewerManager);
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private EventTypeMcpTools tools() {
        return new EventTypeMcpTools(profileManager);
    }

    private void recorded(String code) {
        when(eventViewerManager.eventTypes()).thenReturn(List.of(new EventViewerData(
                List.of("Java Virtual Machine", "GC"), RECORDED_LABEL, code, 128, "JDK", true)));
        when(eventViewerManager.eventColumns(Type.fromCode(code))).thenReturn(List.of(
                new FieldDescription(SUM_OF_PAUSES_FIELD, "Sum of Pauses", "DURATION",
                        "Sum of all the times in which Java execution was paused during the collection"),
                new FieldDescription(LONGEST_PAUSE_FIELD, "Longest Pause", "DURATION",
                        "Longest individual pause during the collection")));
    }

    @Nested
    class DescribeEventType {

        /**
         * The whole point of the tool: the JFR field names, which are what a query has to spell and
         * are reliably not what a reader would guess from the UI's labels.
         */
        @Test
        void namesTheJfrFieldsRatherThanTheirLabels() {
            recorded(RECORDED_TYPE);

            String out = tools().describeEventType(RECORDED_TYPE);

            assertTrue(out.contains("\"name\":\"" + SUM_OF_PAUSES_FIELD + "\""), out);
            assertTrue(out.contains("\"name\":\"" + LONGEST_PAUSE_FIELD + "\""), out);
            assertTrue(out.contains("Sum of Pauses"), out);
        }

        @Test
        void carriesTheCountTheCategoriesAndWhetherTheTypeHasAStack() {
            recorded(RECORDED_TYPE);

            String out = tools().describeEventType(RECORDED_TYPE);

            assertTrue(out.contains("\"eventType\":\"" + RECORDED_TYPE + "\""), out);
            assertTrue(out.contains("\"count\":128"), out);
            assertTrue(out.contains("\"withStackTrace\":true"), out);
            assertTrue(out.contains("\"categories\":[\"Java Virtual Machine\",\"GC\"]"), out);
        }

        @Test
        void linksTheEventsViewNarrowedToThisType() {
            recorded(RECORDED_TYPE);

            String out = tools().describeEventType(RECORDED_TYPE);

            assertTrue(out.contains(EVENTS_VIEW_LINK), out);
            assertTrue(out.contains("eventType=jdk.GarbageCollection"), out);
        }

        /**
         * Resolving the columns of a type the profile does not hold would be an empty answer dressed
         * as a schema, so the refusal happens before the manager is asked for them.
         */
        @Test
        void refusesAnUnrecordedTypeWithoutAskingForItsColumns() {
            recorded(RECORDED_TYPE);

            String out = tools().describeEventType(UNRECORDED_TYPE);

            assertTrue(out.contains("recorded no event type called '" + UNRECORDED_TYPE + "'"), out);
            assertTrue(out.contains("jfr_listEventTypes"), out);
            verify(eventViewerManager, never()).eventColumns(Type.fromCode(UNRECORDED_TYPE));
        }

        @Test
        void refusesAMissingEventTypeAndSaysWhichToolListsThem() {
            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class, () -> tools().describeEventType(null));

            assertTrue(thrown.getMessage().contains("eventType is required"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("jfr_listEventTypes"), thrown.getMessage());
        }

        @Test
        void refusesABlankEventTypeTheSameWay() {
            assertThrows(IllegalArgumentException.class, () -> tools().describeEventType("   "));
        }

        @Test
        void trimsTheEventTypeBeforeMatching() {
            recorded(RECORDED_TYPE);

            String out = tools().describeEventType("  " + RECORDED_TYPE + "  ");

            assertTrue(out.contains("\"eventType\":\"" + RECORDED_TYPE + "\""), out);
            assertFalse(out.contains("recorded no event type"), out);
        }

        @Test
        void sendsTheReaderOnToTheQueryToolsTheseFieldNamesAreFor() {
            recorded(RECORDED_TYPE);

            String out = tools().describeEventType(RECORDED_TYPE);

            assertTrue(out.contains("jfr_queryEvents"), out);
            assertTrue(out.contains("jfr_executeQuery"), out);
        }
    }
}
