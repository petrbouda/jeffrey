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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.thread.ThreadManager;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis;
import cafe.jeffrey.profile.thread.ThreadCommon;
import cafe.jeffrey.profile.thread.ThreadEventDetail;
import cafe.jeffrey.profile.thread.ThreadEventsQuery;
import cafe.jeffrey.profile.thread.ThreadGroupPage;
import cafe.jeffrey.profile.thread.ThreadMembersQuery;
import cafe.jeffrey.profile.thread.ThreadPage;
import cafe.jeffrey.profile.thread.ThreadPageQuery;
import cafe.jeffrey.profile.thread.ThreadSort;
import cafe.jeffrey.profile.thread.ThreadState;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ThreadControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Mock
    ThreadManager threadManager;

    @Test
    void getsActiveThreadsTimeseries() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.threadManager()).thenReturn(threadManager);
        when(threadManager.activeThreadsSerie()).thenReturn(new SingleSerie("threads", List.of()));

        MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread/timeseries"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.name").asString().isEqualTo("threads");
    }

    @Test
    void getsThreadDumpAnalysis() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.threadManager()).thenReturn(threadManager);
        when(threadManager.threadDumpAnalysis()).thenReturn(new ThreadDumpAnalysis(
                new ThreadDumpAnalysis.Header(0, 0, 0, 0, 0, 0),
                List.of(), new TimeseriesData(List.of()), List.of(), List.of(), List.of(), List.of(),
                new ThreadDumpAnalysis.Heatmap(List.of(), List.of())));

        MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread/dumps")).hasStatusOk();
    }

    @Test
    void getsSingleThreadDump() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.threadManager()).thenReturn(threadManager);
        when(threadManager.threadDump(0)).thenReturn(new ParsedDump(0, List.of(), List.of(), ""));

        MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread/dumps/0")).hasStatusOk();
    }

    @Test
    void getsReservedStackActivations() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.threadManager()).thenReturn(threadManager);
        when(threadManager.reservedStackActivations()).thenReturn(List.of());

        MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread/reserved-stack")).hasStatusOk();
    }

    @Nested
    class ThreadPaging {

        private ThreadGroupPage emptyPage() {
            return new ThreadGroupPage(new ThreadCommon(60_000, false, null), List.of(), 0, 18, 18, 1204);
        }

        private ThreadPageQuery capturedQuery() {
            ArgumentCaptor<ThreadPageQuery> captor = ArgumentCaptor.forClass(ThreadPageQuery.class);
            verify(threadManager).threadGroups(captor.capture());
            return captor.getValue();
        }

        /**
         * Opening the page asks for the busiest threads and nothing else — the defaults are what
         * keeps a recording with a thousand threads off the wire.
         */
        @Test
        void defaultsToTheFirstPageOfTheBusiestThreads() {
            when(resolver.resolve("p-1")).thenReturn(profileManager);
            when(profileManager.threadManager()).thenReturn(threadManager);
            when(threadManager.threadGroups(any(ThreadPageQuery.class))).thenReturn(emptyPage());

            MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

            assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread")).hasStatusOk();

            ThreadPageQuery query = capturedQuery();
            assertThat(query.sort()).isEqualTo(ThreadSort.EVENT_COUNT);
            assertThat(query.offset()).isZero();
            assertThat(query.limit()).isEqualTo(50);
            assertThat(query.hasNameFilter()).isFalse();
        }

        @Test
        void passesSortFilterAndOffsetThrough() {
            when(resolver.resolve("p-1")).thenReturn(profileManager);
            when(profileManager.threadManager()).thenReturn(threadManager);
            when(threadManager.threadGroups(any(ThreadPageQuery.class))).thenReturn(emptyPage());

            MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

            assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread"
                    + "?sort=LIFESPAN&nameFilter=connection-adder&offset=50&limit=50"))
                    .hasStatusOk();

            ThreadPageQuery query = capturedQuery();
            assertThat(query.sort()).isEqualTo(ThreadSort.LIFESPAN);
            assertThat(query.nameFilter()).isEqualTo("connection-adder");
            assertThat(query.offset()).isEqualTo(50);
        }

        /**
         * Each row still carries a lane's worth of bands, so an unbounded page would put us back
         * where we started.
         */
        @Test
        void capsThePageSizeWhateverIsAskedFor() {
            when(resolver.resolve("p-1")).thenReturn(profileManager);
            when(profileManager.threadManager()).thenReturn(threadManager);
            when(threadManager.threadGroups(any(ThreadPageQuery.class))).thenReturn(emptyPage());

            MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

            assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread?limit=100000")).hasStatusOk();

            assertThat(capturedQuery().limit()).isEqualTo(250);
        }

        /**
         * The footer counts come from the response, so they have to survive serialization.
         */
        @Test
        void reportsHowManyThreadsTheLanesStandFor() {
            when(resolver.resolve("p-1")).thenReturn(profileManager);
            when(profileManager.threadManager()).thenReturn(threadManager);
            when(threadManager.threadGroups(any(ThreadPageQuery.class))).thenReturn(emptyPage());

            MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

            assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread"))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.totalThreads").asNumber().isEqualTo(1204);
        }

        @Test
        void rejectsANegativeOffset() {
            MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

            assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread?offset=-1")).hasStatus(400);
        }
    }

    @Nested
    class GroupMembers {

        private ThreadMembersQuery capturedQuery() {
            ArgumentCaptor<ThreadMembersQuery> captor = ArgumentCaptor.forClass(ThreadMembersQuery.class);
            verify(threadManager).threadGroupMembers(captor.capture());
            return captor.getValue();
        }

        /**
         * Opening a lane asks for its first 50 threads — a pool of 351 must not arrive whole any
         * more than the ungrouped timeline could.
         */
        @Test
        void defaultToTheFirstPageOfALane() {
            when(resolver.resolve("p-1")).thenReturn(profileManager);
            when(profileManager.threadManager()).thenReturn(threadManager);
            when(threadManager.threadGroupMembers(any(ThreadMembersQuery.class)))
                    .thenReturn(new ThreadPage(new ThreadCommon(60_000, false, null), List.of(), 0, 351, 1204));

            MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

            assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread/members"
                    + "?group=oracleApp:connection-adder"))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.matchedCount").asNumber().isEqualTo(351);

            ThreadMembersQuery query = capturedQuery();
            assertThat(query.groupKey()).isEqualTo("oracleApp:connection-adder");
            assertThat(query.sort()).isEqualTo(ThreadSort.EVENT_COUNT);
            assertThat(query.offset()).isZero();
            assertThat(query.limit()).isEqualTo(50);
        }

        @Test
        void pageThroughALaneOnRequest() {
            when(resolver.resolve("p-1")).thenReturn(profileManager);
            when(profileManager.threadManager()).thenReturn(threadManager);
            when(threadManager.threadGroupMembers(any(ThreadMembersQuery.class)))
                    .thenReturn(new ThreadPage(new ThreadCommon(60_000, false, null), List.of(), 50, 351, 1204));

            MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

            assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread/members"
                    + "?group=http-nio-8080-exec-*&offset=50&sort=NAME"))
                    .hasStatusOk();

            ThreadMembersQuery query = capturedQuery();
            assertThat(query.groupKey()).isEqualTo("http-nio-8080-exec-*");
            assertThat(query.offset()).isEqualTo(50);
            assertThat(query.sort()).isEqualTo(ThreadSort.NAME);
        }

        @Test
        void areCappedLikeTheLanesThemselves() {
            when(resolver.resolve("p-1")).thenReturn(profileManager);
            when(profileManager.threadManager()).thenReturn(threadManager);
            when(threadManager.threadGroupMembers(any(ThreadMembersQuery.class)))
                    .thenReturn(new ThreadPage(new ThreadCommon(60_000, false, null), List.of(), 0, 351, 1204));

            MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

            assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread/members"
                    + "?group=pool-*&limit=100000")).hasStatusOk();

            assertThat(capturedQuery().limit()).isEqualTo(250);
        }

        @Test
        void areRejectedWithoutALane() {
            MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

            assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread/members?group=")).hasStatus(400);
        }
    }

    @Nested
    class BandEvents {

        /**
         * The timeline ships bands without any event fields, so the tooltip asks for the events of
         * the one band under the pointer.
         */
        @Test
        void areLookedUpForTheHoveredBand() {
            when(resolver.resolve("p-1")).thenReturn(profileManager);
            when(profileManager.threadManager()).thenReturn(threadManager);
            when(threadManager.threadEvents(any(ThreadEventsQuery.class)))
                    .thenReturn(List.of(new ThreadEventDetail(1_000, 500, List.of(500, "/tmp/data.log", 4096))));

            MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

            assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread/events"
                    + "?osId=7&javaId=42&state=FILE_WRITE&from=1000&to=1500"))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$[0].values[1]").asString().isEqualTo("/tmp/data.log");

            ArgumentCaptor<ThreadEventsQuery> captor = ArgumentCaptor.forClass(ThreadEventsQuery.class);
            verify(threadManager).threadEvents(captor.capture());
            ThreadEventsQuery query = captor.getValue();
            assertThat(query.threadInfo().javaId()).isEqualTo(42);
            assertThat(query.threadInfo().osId()).isEqualTo(7);
            assertThat(query.state()).isEqualTo(ThreadState.FILE_WRITE);
            assertThat(query.from()).isEqualTo(Duration.ofNanos(1_000));
            assertThat(query.to()).isEqualTo(Duration.ofNanos(1_500));
            assertThat(query.limit())
                    .as("A tooltip renders one event and takes the rest from the band's own count")
                    .isEqualTo(1);
        }

        /**
         * A caller cannot pull a whole burst back through the tooltip endpoint — that is the payload
         * the bands exist to avoid.
         */
        @Test
        void areCappedRegardlessOfTheRequestedLimit() {
            when(resolver.resolve("p-1")).thenReturn(profileManager);
            when(profileManager.threadManager()).thenReturn(threadManager);
            when(threadManager.threadEvents(any(ThreadEventsQuery.class))).thenReturn(List.of());

            MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

            assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread/events"
                    + "?osId=7&javaId=42&state=FILE_WRITE&from=0&to=10&limit=100000"))
                    .hasStatusOk();

            ArgumentCaptor<ThreadEventsQuery> captor = ArgumentCaptor.forClass(ThreadEventsQuery.class);
            verify(threadManager).threadEvents(captor.capture());
            assertThat(captor.getValue().limit()).isEqualTo(20);
        }

        /**
         * The lifespan states are reconstructed from start/end pairs, so there is no event to show —
         * {@link ThreadEventsQuery} rejects them and the web layer answers 400 rather than 500.
         */
        @Test
        void areRejectedForAStateWithoutEventDetail() {
            MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

            assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread/events"
                    + "?osId=7&javaId=42&state=STARTED&from=0&to=10"))
                    .hasStatus(400);
        }
    }

    @Test
    void profileNotFoundReturns404() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost/thread/timeseries"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }
}
