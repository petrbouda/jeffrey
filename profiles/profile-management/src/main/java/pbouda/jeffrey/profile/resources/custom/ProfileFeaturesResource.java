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

package pbouda.jeffrey.profile.resources.custom;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.profile.feature.FeatureType;
import pbouda.jeffrey.profile.manager.ProfileFeaturesManager;

import java.util.List;

public class ProfileFeaturesResource {

    private final ProfileFeaturesManager featuresManager;

    public ProfileFeaturesResource(ProfileFeaturesManager featuresManager) {
        this.featuresManager = featuresManager;
    }

    @GET
    @Path("disabled")
    public List<FeatureType> disabledFeatures() {
        return featuresManager.getDisabledFeatures();
    }
}
