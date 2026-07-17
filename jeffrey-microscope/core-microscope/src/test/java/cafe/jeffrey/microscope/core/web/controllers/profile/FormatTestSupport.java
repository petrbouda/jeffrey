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

import cafe.jeffrey.pprofparser.PprofFormat;
import cafe.jeffrey.provider.profile.api.EventCategoryResolver;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.provider.profile.api.RecordingFormat;
import cafe.jeffrey.provider.profile.api.RecordingFormatCapabilities;
import cafe.jeffrey.provider.profile.api.RecordingFormatRegistry;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.time.Instant;
import java.util.List;

/**
 * Shared fixtures for controller tests that need format-aware behavior: a registry with the real
 * pprof format plus a curated JFR-like default (parsers are never invoked by web-layer tests), and
 * a minimal {@link ProfileInfo} for stubbing {@code ProfileManager.info()}.
 */
final class FormatTestSupport {

    private static final Instant PROFILING_STARTED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant PROFILING_FINISHED_AT = Instant.parse("2026-01-01T00:01:00Z");

    private FormatTestSupport() {
    }

    static RecordingFormatRegistry recordingFormats() {
        return RecordingFormatRegistry.of(List.of(new PprofFormat()), new CuratedDefaultFormat());
    }

    static ProfileInfo profileInfo(String profileId, RecordingEventSource eventSource) {
        return new ProfileInfo(
                profileId,
                null,
                null,
                "profile-" + profileId,
                eventSource,
                PROFILING_STARTED_AT,
                PROFILING_FINISHED_AT,
                PROFILING_STARTED_AT,
                true,
                false,
                null);
    }

    private static final class CuratedDefaultFormat implements RecordingFormat {

        @Override
        public RecordingEventSource eventSource() {
            return RecordingEventSource.JDK;
        }

        @Override
        public SupportedRecordingFile fileType() {
            return SupportedRecordingFile.JFR;
        }

        @Override
        public RecordingEventParser eventParser() {
            return null;
        }

        @Override
        public RecordingInformationParser informationParser() {
            return null;
        }

        @Override
        public RecordingFormatCapabilities capabilities() {
            return new RecordingFormatCapabilities(true, true);
        }

        @Override
        public EventCategoryResolver eventCategoryResolver() {
            return null;
        }
    }
}
