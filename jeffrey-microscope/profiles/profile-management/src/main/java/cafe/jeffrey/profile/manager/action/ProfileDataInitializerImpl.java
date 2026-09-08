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
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.persistence.DatabaseLease;
import cafe.jeffrey.shared.persistence.DatabaseManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;

public class ProfileDataInitializerImpl implements ProfileDataInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileDataInitializerImpl.class);

    private static final String SPAN_THREAD_VIEWER = "threads.rows";
    private static final String SPAN_AUTO_ANALYSIS = "analysis.autoAnalysis";

    private static final String COMPONENT_THREAD_VIEWER = "Thread Viewer";
    private static final String COMPONENT_AUTO_ANALYSIS = "Auto Analysis";

    private final DatabaseManager databaseManager;
    private final Executor executor;
    private final Function<Path, List<AutoAnalysisResult>> ruleSet;

    /**
     * @param executor where the warming runs. In production this is the bulk pool, never the
     *                 interactive one: a profile that has just been imported must not push a
     *                 flamegraph someone is waiting on out of the way.
     * @param ruleSet  what evaluates the JMC rules over a recording file. Taken as a collaborator
     *                 rather than reached for statically, because this is the one warm that starts
     *                 before there is a {@link ProfileManager} to ask for it.
     */
    public ProfileDataInitializerImpl(
            DatabaseManager databaseManager,
            Executor executor,
            Function<Path, List<AutoAnalysisResult>> ruleSet) {

        this.databaseManager = databaseManager;
        this.executor = executor;
        this.ruleSet = ruleSet;
    }

    /**
     * Started before the parse rather than after it, because the rule set and the parse read the
     * same file and share nothing else: the JMC toolkit loads the recording itself, and the only
     * thing this run ever writes is the one cache row {@link #initialize} stores at the end. Waiting
     * for the parse first would have cost the import both passes end to end instead of the longer
     * of the two.
     * <p>
     * The recording is read twice over, so the two passes now hold their peaks at the same moment.
     * That is the price of the overlap, and the reason this is the warm that is allowed to fail
     * without anybody hearing about it.
     */
    @Override
    public CompletableFuture<List<AutoAnalysisResult>> startAutoAnalysis(
            ProfileInfo profileInfo, Path recordingPath) {

        // pprof/OTLP imports are stack samples with no JFR events behind them, and the rule set only
        // understands JFR. A recording whose file is not there cannot be read a second time either.
        if (profileInfo.eventSource().isFlamegraphOnlyImport()) {
            LOG.info("Skipping auto analysis for a flamegraph-only profile: "
                            + "profile_id={} profile_name={} event_source={}",
                    profileInfo.id(), profileInfo.name(), profileInfo.eventSource());
            return CompletableFuture.completedFuture(null);
        }
        if (recordingPath == null || !Files.exists(recordingPath)) {
            LOG.info("Skipping auto analysis, the recording file is not available: "
                            + "profile_id={} profile_name={}",
                    profileInfo.id(), profileInfo.name());
            return CompletableFuture.completedFuture(null);
        }

        LOG.info("Starting auto analysis alongside the parse: profile_id={} recording={}",
                profileInfo.id(), recordingPath);

        return CompletableFuture
                .supplyAsync(Tracer.fork(SPAN_AUTO_ANALYSIS, () -> ruleSet.apply(recordingPath)), executor)
                .exceptionally(throwable -> {
                    warmFailed(COMPONENT_AUTO_ANALYSIS, profileInfo, throwable);
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> initialize(
            ProfileManager profileManager, CompletableFuture<List<AutoAnalysisResult>> autoAnalysis) {

        ProfileInfo profileInfo = profileManager.info();

        // pprof/OTLP profiles are stack-sample imports visualized only as flamegraphs (generated on
        // demand). The thread viewer below reads JFR-shaped fields these imports do not have, and
        // the analysis was never started for them.
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

        CompletableFuture<Void> threads = warm(SPAN_THREAD_VIEWER, COMPONENT_THREAD_VIEWER, profileInfo,
                () -> profileManager.threadManager().threadRows());

        CompletableFuture<Void> analysis = storeAutoAnalysis(profileManager, profileInfo, autoAnalysis);

        return CompletableFuture.allOf(threads, analysis)
                .whenComplete((_, _) -> {
                    lease.close();
                    LOG.info("Cached views of the profile have been warmed: profile_id={} profile_name={}",
                            profileInfo.id(), profileInfo.name());
                });
    }

    /**
     * Caches what the run started before the parse produced. The findings are held in memory until
     * here rather than written when they are ready, so the one row they occupy lands after the parse
     * and the re-clustering are finished with the database rather than beside them.
     */
    private CompletableFuture<Void> storeAutoAnalysis(
            ProfileManager profileManager,
            ProfileInfo profileInfo,
            CompletableFuture<List<AutoAnalysisResult>> autoAnalysis) {

        if (autoAnalysis == null) {
            return CompletableFuture.completedFuture(null);
        }

        return autoAnalysis
                .thenAccept(results -> {
                    // Null means the run was skipped or failed, and both have been reported already.
                    if (results != null) {
                        profileManager.autoAnalysisManager().store(results);
                    }
                })
                .exceptionally(throwable -> {
                    warmFailed(COMPONENT_AUTO_ANALYSIS, profileInfo, throwable);
                    return null;
                });
    }

    /**
     * Runs one view's warm-up.
     * <p>
     * A failure is logged and swallowed rather than propagated. Every view is computed on demand
     * when the cache misses, so a profile whose view failed to warm is a slower profile, not a
     * broken one -- and letting it fail the batch would take the lease's release down with it, and
     * now the import stage that waits for the batch as well.
     */
    private CompletableFuture<Void> warm(
            String span, String component, ProfileInfo profileInfo, Runnable work) {

        // ScopedValue does not cross an executor boundary; fork captures the enclosing span here, on
        // the submitting thread, and re-establishes it inside the task. Without it each view would
        // start a trace of its own rather than appearing under the initialization that asked for it.
        return CompletableFuture
                .runAsync(Tracer.fork(span, work), executor)
                .exceptionally(throwable -> {
                    warmFailed(component, profileInfo, throwable);
                    return null;
                });
    }

    private static void warmFailed(String component, ProfileInfo profileInfo, Throwable throwable) {
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
    }
}
