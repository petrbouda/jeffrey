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

package pbouda.jeffrey.resources.project.profile;

import jakarta.ws.rs.Path;
import pbouda.jeffrey.manager.ProfileManager;

public class ProfileDiffResource {

    private final ProfileManager primaryProfileManager;
    private final ProfileManager secondaryProfileManager;

    public ProfileDiffResource(ProfileManager primaryProfileManager, ProfileManager secondaryProfileManager) {
        this.primaryProfileManager = primaryProfileManager;
        this.secondaryProfileManager = secondaryProfileManager;
    }

    @Path("/differential-flamegraph")
    public FlamegraphDiffResource flamegraphDiffResource() {
        return new FlamegraphDiffResource(
                primaryProfileManager.info(), primaryProfileManager.diffFlamegraphManager(secondaryProfileManager));
    }

    @Path("/differential-timeseries")
    public TimeseriesResource timeseriesDiffResource() {
        return new TimeseriesResource(primaryProfileManager.diffTimeseriesManager(secondaryProfileManager));
    }
}
