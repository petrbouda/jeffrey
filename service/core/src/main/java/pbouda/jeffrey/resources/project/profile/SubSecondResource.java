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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.POST;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.ProfilingStartEnd;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.resources.request.GetSubSecondRequest;
import pbouda.jeffrey.manager.SubSecondManager;

import static pbouda.jeffrey.resources.project.profile.FlamegraphResource.toTimeRange;

public class SubSecondResource {

    private final ProfileInfo profileInfo;
    private final SubSecondManager subSecondManager;

    public SubSecondResource(ProfileInfo profileInfo, SubSecondManager subSecondManager) {
        this.profileInfo = profileInfo;
        this.subSecondManager = subSecondManager;
    }

    @POST
    public JsonNode generate(GetSubSecondRequest request) {
        RelativeTimeRange relativeTimeRange = null;
        if (request.timeRange() != null) {
            ProfilingStartEnd startEnd = new ProfilingStartEnd(
                    profileInfo.profilingStartedAt(), profileInfo.profilingFinishedAt());
            relativeTimeRange = toTimeRange(request.timeRange()).toRelativeTimeRange(startEnd);
        }
        return subSecondManager.generate(request.eventType(), request.useWeight(), relativeTimeRange);
    }
}
