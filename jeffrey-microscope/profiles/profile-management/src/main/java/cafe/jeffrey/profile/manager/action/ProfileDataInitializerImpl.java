/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.manager.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.Tracer;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.profile.manager.ProfileManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

public class ProfileDataInitializerImpl implements ProfileDataInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileDataInitializerImpl.class);

    private static final String SPAN_EVENT_VIEWER = "eventviewer.tree";
    private static final String SPAN_GUARDIAN = "guardian.results";
    private static final String SPAN_THREAD_VIEWER = "threads.rows";

    private final boolean blocking;
    private final boolean concurrent;

    public ProfileDataInitializerImpl(boolean blocking, boolean concurrent) {
        this.blocking = blocking;
        this.concurrent = concurrent;
    }

    @Override
    public void initialize(ProfileManager profileManager) {
        ProfileInfo profileInfo = profileManager.info();

        LOG.info("Start initializing data of the profile: profile_id={} profile_name={} blocking={} concurrent={}",
                profileInfo.id(), profileInfo.name(), blocking, concurrent);

        // pprof/OTLP profiles are stack-sample imports visualized only as flamegraphs (generated on demand).
        // The pre-computed views below — event viewer, guardian, thread viewer — are JFR-specific and read
        // JFR-shaped fields these imports don't have, so they are skipped for such sources.
        if (profileInfo.eventSource().isFlamegraphOnlyImport()) {
            LOG.info("Skipping JFR-specific initialization for a flamegraph-only profile: "
                            + "profile_id={} profile_name={} event_source={}",
                    profileInfo.id(), profileInfo.name(), profileInfo.eventSource());
            return;
        }

        ExecutorService executor = this.concurrent ? Schedulers.sharedParallel() : Schedulers.sharedSingle();

        // ScopedValue does not cross an executor boundary; fork captures the enclosing span here and
        // re-establishes it inside each task. Without this the three views below would each start
        // their own trace instead of appearing under the initialization that asked for them.

        // Create and cache data for EventViewer
        var viewerFuture = CompletableFuture
                .runAsync(Tracer.fork(SPAN_EVENT_VIEWER, SpanKind.INTERNAL, () -> {
                    profileManager.eventViewerManager().eventTypesTree();
                    LOG.info("Event Viewer has been initialized: profile_id={} profile_name={}",
                            profileInfo.id(), profileInfo.name());
                }), executor)
                .exceptionally(toException("EventViewer", profileInfo));

        // Create Guardian results
        var guardianFuture = CompletableFuture
                .runAsync(Tracer.fork(SPAN_GUARDIAN, SpanKind.INTERNAL, () -> {
                    profileManager.guardianManager().guardResults();
                    LOG.info("Guardian Results has been generated: profile_id={} profile_name={}",
                            profileInfo.id(), profileInfo.name());
                }), executor)
                .exceptionally(toException("Guardian", profileInfo));

        // Create Thread View
        var threadsFuture = CompletableFuture
                .runAsync(Tracer.fork(SPAN_THREAD_VIEWER, SpanKind.INTERNAL, () -> {
                    profileManager.threadManager().threadRows();
                    LOG.info("Thread Viewer has been generated: profile_id={} profile_name={}",
                            profileInfo.id(), profileInfo.name());
                }), executor)
                .exceptionally(toException("ThreadViewer", profileInfo));

        if (blocking) {
            CompletableFuture.allOf(
                    viewerFuture,
                    guardianFuture,
                    threadsFuture
            ).join();
        }
    }

    private static Function<Throwable, Void> toException(String component, ProfileInfo profileInfo) {
        return throwable -> {
            String message = "Failed to generate %s: profile_id=%s profile_name=%s"
                    .formatted(component, profileInfo.id(), profileInfo.name());
            if (throwable instanceof Error error) {
                throw error;
            }
            throw Exceptions.internal(message, (Exception) throwable);
        };
    }
}
