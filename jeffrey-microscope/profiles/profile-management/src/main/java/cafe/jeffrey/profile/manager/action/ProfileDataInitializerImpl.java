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

package cafe.jeffrey.profile.manager.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.jfr.events.trace.Tracer;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.persistence.DatabaseLease;
import cafe.jeffrey.shared.persistence.DatabaseManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import cafe.jeffrey.shared.notification.NotificationCategory;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;
import cafe.jeffrey.jfr.events.notification.Severity;

public class ProfileDataInitializerImpl implements ProfileDataInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileDataInitializerImpl.class);

    private static final String SPAN_THREAD_VIEWER = "threads.rows";
    private static final String SPAN_AUTO_ANALYSIS = "analysis.autoAnalysis";

    private final DatabaseManager databaseManager;
    private final Executor executor;

    /**
     * @param executor where the warming runs. In production this is the bulk pool, never the
     *                 interactive one: a profile that has just been imported must not push a
     *                 flamegraph someone is waiting on out of the way.
     */
    public ProfileDataInitializerImpl(DatabaseManager databaseManager, Executor executor) {
        this.databaseManager = databaseManager;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Void> initialize(ProfileManager profileManager) {
        ProfileInfo profileInfo = profileManager.info();

        // pprof/OTLP profiles are stack-sample imports visualized only as flamegraphs (generated on
        // demand). The views below -- the thread viewer and the auto analysis -- are JFR-specific:
        // one reads JFR-shaped fields these imports don't have, the other hands the recording file
        // to the JMC rule set, which only understands JFR. Both are skipped for such sources.
        if (profileInfo.eventSource().isFlamegraphOnlyImport()) {
            LOG.info("Skipping JFR-specific initialization for a flamegraph-only profile: "
                            + "profile_id={} profile_name={} event_source={}",
                    profileInfo.id(), profileInfo.name(), profileInfo.eventSource());
            return CompletableFuture.completedFuture(null);
        }

        LOG.info("Start warming the cached views of the profile: profile_id={} profile_name={}",
                profileInfo.id(), profileInfo.name());

        // Taken here, on the initializing thread, while the pool is demonstrably alive -- the parse
        // has just finished writing through it. Acquiring from inside the warming task instead would
        // leave a window in which the pool could be idle-evicted between this method returning and
        // the task actually starting.
        DatabaseLease lease = databaseManager.acquire(profileInfo.id());

        CompletableFuture<Void> threads = warm(SPAN_THREAD_VIEWER, "Thread Viewer", profileInfo,
                () -> profileManager.threadManager().threadRows());

        // Auto Analysis is a cache like the thread bands, but it is the only one that does not read
        // the profile database: the JMC rule set loads the original recording file a second time.
        // That is why it used to wait for someone to press a button -- and why warming it here is
        // worth the second parse, since until it is computed the summary dashboard, the MCP
        // jvm_autoAnalysis tool and the IDE recording panel all show nothing rather than findings.
        // Skipped without complaint when the recording file is no longer on disk.
        CompletableFuture<Void> autoAnalysis = warmAutoAnalysis(profileManager, profileInfo);

        return CompletableFuture.allOf(threads, autoAnalysis)
                .whenComplete((_, _) -> {
                    lease.close();
                    LOG.info("Cached views of the profile have been warmed: profile_id={} profile_name={}",
                            profileInfo.id(), profileInfo.name());
                });
    }

    private CompletableFuture<Void> warmAutoAnalysis(
            ProfileManager profileManager, ProfileInfo profileInfo) {

        if (!profileManager.autoAnalysisManager().canGenerate()) {
            LOG.info("Skipping auto analysis, the recording file is not available: "
                            + "profile_id={} profile_name={}",
                    profileInfo.id(), profileInfo.name());
            return CompletableFuture.completedFuture(null);
        }

        return warm(SPAN_AUTO_ANALYSIS, "Auto Analysis", profileInfo,
                () -> profileManager.autoAnalysisManager().generate());
    }

    /**
     * Runs one view's warm-up.
     * <p>
     * A failure is logged and swallowed rather than propagated. Every view is computed on demand
     * when the cache misses, so a profile whose view failed to warm is a slower profile, not a
     * broken one -- and letting it fail the batch would take the lease's release down with it.
     */
    private CompletableFuture<Void> warm(
            String span, String component, ProfileInfo profileInfo, Runnable work) {

        // ScopedValue does not cross an executor boundary; fork captures the enclosing span here, on
        // the submitting thread, and re-establishes it inside the task. Without it each view would
        // start a trace of its own rather than appearing under the initialization that asked for it.
        return CompletableFuture
                .runAsync(Tracer.fork(span, work), executor)
                .exceptionally(throwable -> {
                    LOG.warn("Failed to warm a cached view, it will be computed on demand: "
                                    + "component={} profile_id={} profile_name={}",
                            component, profileInfo.id(), profileInfo.name(), throwable);

                    // LOW: nothing is lost, the view is simply computed when someone opens it. Worth
                    // saying only because the cost moves -- the first reader pays what the import was
                    // meant to have paid, and this is the only thing that connects the two.
                    Notifications.of(NotificationType.PROFILE_VIEW_WARMUP_FAILED)
                            .attribute("component", component)
                            .attribute("profileId", profileInfo.id())
                            .errorType(throwable)
                            .emit();

                    return null;
                });
    }
}
