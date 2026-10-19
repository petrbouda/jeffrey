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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.common.exceptions.ThrowOrigin;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionRateTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionSiteStat;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionTypeStat;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionsOverview;
import cafe.jeffrey.profile.manager.model.exceptions.ThrowAnalysis;
import cafe.jeffrey.profile.manager.model.exceptions.ThrowAnalysisBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.ProfileExceptionRepository;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

public class ExceptionsManagerImpl implements ExceptionsManager {

    private static final String THROWABLES_FIELD = "throwables";

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;
    private final ProfileExceptionRepository exceptionRepository;

    public ExceptionsManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository,
            ProfileExceptionRepository exceptionRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
        this.exceptionRepository = exceptionRepository;
    }

    @Override
    public ExceptionsOverview overview() {
        return overview(analysis());
    }

    @Override
    public ExceptionsOverview overview(ThrowAnalysis analysis) {
        long totalThrowables = eventRepository.latestJsonFields(Type.EXCEPTION_STATISTICS)
                .map(fields -> Math.max(0, Json.readLong(fields, THROWABLES_FIELD)))
                .orElse(0L);

        long errors = analysis.errorCount();

        return new ExceptionsOverview(
                totalThrowables,
                analysis.throwCount() - errors,
                errors,
                analysis.types().size(),
                eventRepository.containsEventType(Type.JAVA_EXCEPTION_THROW),
                eventRepository.containsEventType(Type.JAVA_ERROR_THROW),
                analysis.countOf(ThrowOrigin.APPLICATION),
                analysis.countOf(ThrowOrigin.JVM_INTERNAL));
    }

    @Override
    public TimeseriesData timeline() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        // The builder computes deltas between consecutive cumulative samples — the stream
        // must be chronological.
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.EXCEPTION_STATISTICS)
                .withJsonFields()
                .orderedByTime();

        return eventStreamRepository.genericStreaming(configurer, new ExceptionRateTimeseriesBuilder(timeRange));
    }

    @Override
    public List<ExceptionTypeStat> topTypes() {
        return analysis().types();
    }

    @Override
    public List<ExceptionSiteStat> sites() {
        return analysis().sites();
    }

    @Override
    public ThrowAnalysis analysis() {
        boolean recorded = eventRepository.containsEventType(Type.JAVA_EXCEPTION_THROW)
                || eventRepository.containsEventType(Type.JAVA_ERROR_THROW);
        if (!recorded) {
            return ThrowAnalysis.EMPTY;
        }
        return ThrowAnalysisBuilder.build(
                exceptionRepository.throwCounts(),
                exceptionRepository.throwMessages(),
                exceptionRepository.throwStacktraces());
    }
}
