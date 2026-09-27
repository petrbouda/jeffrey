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

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.CpuTimeSampleLoss;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * A mocked JFR profile for the tests that read a whole profile rather than one tool's slice of it:
 * a minute-long recording with nothing recorded, no features switched off, no sampler loss and no
 * auto-analysis computed. A test stubs on top of it only what it is about.
 */
public final class ProfileManagerFixture {

    private static final Instant STARTED = Instant.EPOCH;
    private static final Instant FINISHED = STARTED.plusSeconds(60);

    private ProfileManagerFixture() {
    }

    public static ProfileManager jfrProfile(String profileId) {
        return profile(profileId, RecordingEventSource.JDK);
    }

    /** The same profile, imported from another source: a pprof or OTLP sample set, a heap dump. */
    public static ProfileManager profile(String profileId, RecordingEventSource source) {
        // Lenient: these are defaults most tests never reach, and a strict session would call them unused.
        ProfileManager manager = mock(ProfileManager.class,
                withSettings().defaultAnswer(RETURNS_DEEP_STUBS).strictness(Strictness.LENIENT));
        when(manager.info()).thenReturn(new ProfileInfo(profileId, "project", "workspace", profileId,
                source, STARTED, FINISHED, STARTED, true, false, "recording-" + profileId));
        when(manager.samplerHealthManager().cpuTimeSampleLoss()).thenReturn(CpuTimeSampleLoss.EMPTY);
        when(manager.featuresManager().getDisabledFeatures()).thenReturn(List.of());
        when(manager.autoAnalysisManager().analysisResults()).thenReturn(List.of());
        when(manager.flamegraphManager().eventSummaries()).thenReturn(List.of());
        when(manager.flamegraphManager().allEventSummaries()).thenReturn(List.of());
        return manager;
    }
}
