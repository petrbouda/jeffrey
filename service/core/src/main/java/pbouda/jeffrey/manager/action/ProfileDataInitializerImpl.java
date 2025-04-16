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

package pbouda.jeffrey.manager.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.Schedulers;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.manager.ProfileManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class ProfileDataInitializerImpl implements ProfileDataInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileDataInitializerImpl.class);

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

        ExecutorService executor = this.concurrent ? Schedulers.sharedParallel() : Schedulers.sharedSingle();

        // Create and cache data for EventViewer
        var viewerFuture = CompletableFuture.runAsync(
                () -> {
                    profileManager.eventViewerManager().eventTypesTree();
                    LOG.info("Event Viewer has been initialized: profile_id={} profile_name={}",
                            profileInfo.id(), profileInfo.name());
                }, executor);

        // Create Guardian results
        var guardianFuture = CompletableFuture.runAsync(
                () -> {
                    profileManager.guardianManager().guardResults();
                    LOG.info("Guardian Results has been generated: profile_id={} profile_name={}",
                            profileInfo.id(), profileInfo.name());
                }, executor);

        // Create Thread View
        var threadsFuture = CompletableFuture.runAsync(
                () -> {
                    profileManager.threadManager().threadRows();
                    LOG.info("Thread Viewer has been generated: profile_id={} profile_name={}",
                            profileInfo.id(), profileInfo.name());
                }, executor);

        if (blocking) {
            CompletableFuture.allOf(
                    viewerFuture,
                    guardianFuture,
                    threadsFuture).join();
        }
    }
}
