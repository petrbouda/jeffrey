/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.manager.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.Schedulers;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.manager.ProfileManager;

import java.util.concurrent.CompletableFuture;

public class ProfileInitializerImpl implements ProfileInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileInitializerImpl.class);

    private final boolean async;

    public ProfileInitializerImpl(boolean async) {
        this.async = async;
    }

    @Override
    public void execute(ProfileManager profileManager) {
        LOG.info("Start initializing profile: profile_id={} profile_name={} async={}",
                profileManager.info().id(), profileManager.info().name(), async);

        ProfileInfo info = profileManager.info();

        // Create and cache Information
        var configFuture = CompletableFuture.runAsync(
                () -> {
                    profileManager.profileConfigurationManager().information();
                    LOG.info("Profile Configuration has been initialized: profile_id={} profile_name={}",
                            info.id(), info.name());
                },
                Schedulers.parallel());

        // Create and cache AutoAnalysis
        var analysisFuture = CompletableFuture.runAsync(
                () -> {
                    profileManager.autoAnalysisManager().analysisResults();
                    LOG.info("Auto-analysis has been initialized: profile_id={} profile_name={}",
                            info.id(), info.name());
                },
                Schedulers.parallel());

        // Create and cache data for EventViewer
        var viewerFuture = CompletableFuture.runAsync(
                () -> {
                    profileManager.eventViewerManager().allEventTypes();
                    LOG.info("Event Viewer has been initialized: profile_id={} profile_name={}",
                            info.id(), info.name());
                },
                Schedulers.parallel());

        // Create information summary for all events (it also initializes `Active Settings`)
        var summariesFuture = CompletableFuture.runAsync(
                () -> {
                    profileManager.flamegraphManager().eventSummaries();
                    LOG.info("Event Summaries has been initialized: profile_id={} profile_name={}",
                            info.id(), info.name());
                },
                Schedulers.parallel());

        // Create Guardian results
        var guardianFuture = CompletableFuture.runAsync(
                () -> {
                    profileManager.guardianManager().guardResults();
                    LOG.info("Guardian Results has been generated: profile_id={} profile_name={}",
                            info.id(), info.name());
                },
                Schedulers.parallel());

        // Create Thread View
        var threadsFuture = CompletableFuture.runAsync(
                () -> {
                    profileManager.threadManager().threadRows();
                    LOG.info("Thread Viewer has been generated: profile_id={} profile_name={}",
                            info.id(), info.name());
                },
                Schedulers.parallel());

        if (!async) {
            CompletableFuture.allOf(
                    configFuture,
                    analysisFuture,
                    viewerFuture,
                    summariesFuture,
                    guardianFuture,
                    threadsFuture).join();
        }
    }
}
