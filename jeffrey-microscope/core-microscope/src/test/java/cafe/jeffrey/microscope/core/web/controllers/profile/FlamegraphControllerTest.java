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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.common.config.GraphComponents;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.thread.ThreadManager;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.resources.request.GenerateFlamegraphRequest;
import cafe.jeffrey.shared.common.GraphType;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ThreadInfo;
import cafe.jeffrey.shared.common.model.Type;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class FlamegraphControllerTest {

    private static final String GROUP = "oracleApp:connection-adder*";

    /** A pool that mixes both kinds of identity, as a group of real threads does. */
    private static final List<ThreadInfo> POOL = List.of(
            new ThreadInfo(41, 12, "oracleApp:connection-adder-12"),
            new ThreadInfo(42, 13, "oracleApp:connection-adder-13"),
            new ThreadInfo(43, -1, "oracleApp:connection-adder"));

    private static final ProfileInfo PROFILE = new ProfileInfo(
            "p-1", "proj-1", "ws-1", "profile", RecordingEventSource.JDK,
            Instant.parse("2025-01-15T10:00:00Z"), Instant.parse("2025-01-15T10:01:00Z"),
            Instant.parse("2025-01-15T10:02:00Z"), true, false, "rec-1");

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Mock
    FlamegraphManager flamegraphManager;

    @Mock
    ThreadManager threadManager;

    @Test
    void listsEvents() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
        when(flamegraphManager.eventSummaries()).thenReturn(List.of());

        MockMvcTester mvc = mockMvcTesterFor(new FlamegraphController(resolver, new JfrFlamegraphPanelProvider()));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/flamegraph/events"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }

    @Test
    void listsAllEightPanelSectionsInOrder() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
        when(flamegraphManager.eventSummaries()).thenReturn(List.of());

        MockMvcTester mvc = mockMvcTesterFor(new FlamegraphController(resolver, new JfrFlamegraphPanelProvider()));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/flamegraph/panels"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].section").asArray()
                .containsExactly("execution", "cpu-time", "method", "wall",
                        "allocation", "native-alloc", "native-leak", "blocking");
    }

    /**
     * A graph opened from a collapsed timeline lane names the group. The lane has no thread of its
     * own and the page holds at most a slice of its members, so every thread behind it has to be
     * resolved here rather than sent.
     */
    @Test
    void aGraphScopedToAThreadGroupCoversEveryThreadInIt() {
        when(profileManager.info()).thenReturn(PROFILE);
        when(profileManager.threadManager()).thenReturn(threadManager);
        when(threadManager.threadGroupThreads(GROUP)).thenReturn(POOL);

        GraphParameters params = FlamegraphController.mapToGenerateRequest(
                profileManager, request(null, GROUP), GraphType.PRIMARY);

        assertThat(params.threads()).isEqualTo(POOL);
    }

    @Test
    void aGraphScopedToOneThreadStillCarriesThatThreadAlone() {
        when(profileManager.info()).thenReturn(PROFILE);
        ThreadInfo worker = new ThreadInfo(41, 12, "oracleApp:connection-adder-12");

        GraphParameters params = FlamegraphController.mapToGenerateRequest(
                profileManager, request(worker, null), GraphType.PRIMARY);

        assertThat(params.threads()).containsExactly(worker);
    }

    @Test
    void aGraphNamingNoThreadAtAllCoversTheWholeRecording() {
        when(profileManager.info()).thenReturn(PROFILE);

        GraphParameters params = FlamegraphController.mapToGenerateRequest(
                profileManager, request(null, null), GraphType.PRIMARY);

        assertThat(params.threads()).isEmpty();
    }

    /**
     * A group exists only because threads fell into it, so a key that resolves to nothing is a key
     * that no longer names anything — and graphing the whole recording instead would look like an
     * answer.
     */
    @Test
    void anUnknownThreadGroupIsRejected() {
        when(profileManager.info()).thenReturn(PROFILE);
        when(profileManager.threadManager()).thenReturn(threadManager);
        when(threadManager.threadGroupThreads("ghosts*")).thenReturn(List.of());

        assertThatThrownBy(() -> FlamegraphController.mapToGenerateRequest(
                profileManager, request(null, "ghosts*"), GraphType.PRIMARY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghosts*");
    }

    private static GenerateFlamegraphRequest request(ThreadInfo threadInfo, String threadGroup) {
        return new GenerateFlamegraphRequest(
                null, Type.EXECUTION_SAMPLE, null, null, false, false, false, false, false,
                threadInfo, threadGroup, GraphComponents.BOTH, List.of());
    }

    @Test
    void profileNotFoundReturns404() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new FlamegraphController(resolver, new JfrFlamegraphPanelProvider()));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost/flamegraph/events"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }
}
