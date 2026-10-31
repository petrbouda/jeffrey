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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.flamegraph.api.DbBasedFlamegraphGenerator;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.model.EventSummary;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.PrimaryFlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlamegraphRecordedTypesTest {

    @Test
    void recordedPprofDimensionMustStillExport() {
        assertExportsRecordedType("cpu", RecordingEventSource.PPROF);
    }

    @Test
    void recordedJfrSocketReadMustStillExport() {
        assertExportsRecordedType("jdk.SocketRead", RecordingEventSource.JDK);
    }

    private void assertExportsRecordedType(String code, RecordingEventSource source) {
        EventSummary summary = new EventSummary(code, code, source, null, 12, 200, true, false,
                List.of(), Map.of(), Map.of());
        ProfileEventTypeRepository repository = mock(ProfileEventTypeRepository.class);
        when(repository.eventSummaries()).thenReturn(List.of(summary));
        when(repository.eventSummaries(any(List.class))).thenAnswer(invocation -> {
            List<Type> requested = invocation.getArgument(0);
            return requested.stream().anyMatch(type -> type.code().equals(code))
                    ? List.of(summary) : List.of();
        });
        DbBasedFlamegraphGenerator generator = mock(DbBasedFlamegraphGenerator.class);
        when(generator.generateAiExport(any(), any())).thenReturn("## Call tree\n");
        PrimaryFlamegraphManager manager = new PrimaryFlamegraphManager(repository, generator);
        assertEquals(1, manager.allEventSummaries().size(), "the type is recorded");
        assertEquals(0, manager.eventSummaries().size(), "the presentation catalog omits this type");

        Instant start = Instant.parse("2026-10-01T12:00:00Z");
        ProfileManager profile = mock(ProfileManager.class);
        when(profile.info()).thenReturn(new ProfileInfo("review-profile", "project", "workspace", "Review", source,
                start, start.plusSeconds(60), start, true, false, "recording"));
        when(profile.flamegraphManager()).thenReturn(manager);
        FlamegraphMcpTools tools = new FlamegraphMcpTools(profile, new JfrFlamegraphPanelProvider(),
                new StackSampleFlamegraphPanelProvider(), new AdvertisedFamilies(Set.of("flamegraph")));

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        try {
            var result = tools.export(code, null, null, null, null, null, null, null, null, null);
            assertEquals("OK", result.structuredContent().get("status").asString(), result.text());
            ArgumentCaptor<GraphParameters> parameters = ArgumentCaptor.forClass(GraphParameters.class);
            verify(generator).generateAiExport(parameters.capture(), any());
            assertEquals(code, parameters.getValue().eventType().code());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
