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

package pbouda.jeffrey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.controller.model.ProfileIdRequest;
import pbouda.jeffrey.exception.Exceptions;
import pbouda.jeffrey.guardian.guard.Guard.Category;
import pbouda.jeffrey.guardian.guard.GuardAnalysisResult;
import pbouda.jeffrey.guardian.guard.GuardVisualization;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/guardian")
public class GuardianController {

    private final ProfilesManager profilesManager;

    @Autowired
    public GuardianController(ProfilesManager profilesManager) {
        this.profilesManager = profilesManager;
    }

    @PostMapping
    public Map<Category, List<GuardAnalysisResult>> list(@RequestBody ProfileIdRequest request) {
        ProfileManager profileManager = profilesManager.getProfile(request.profileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        List<GuardAnalysisResult> guardAnalysisResults = profileManager
                .guardianManager()
                .guardResults();

        Map<Category, List<GuardAnalysisResult>> output = new TreeMap<>();
        for (GuardAnalysisResult result : guardAnalysisResults) {
            output.compute(result.category(), (category, results) -> {
                if (results != null) {
                    results.add(result);
                    return results;
                } else {
                    List<GuardAnalysisResult> r = new ArrayList<>();
                    r.add(result);
                    return r;
                }
            });
        }

        return output;
    }

    @PostMapping("/flamegraph/generate")
    public JsonNode generateFlamegraph(@RequestBody GuardVisualization request) {
        ProfileManager profileManager = profilesManager.getProfile(request.primaryProfileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return profileManager.guardianManager()
                .generateFlamegraph(request);
    }

    @PostMapping("/timeseries/generate")
    public ArrayNode generateTimeseries(@RequestBody GuardVisualization request) {
        ProfileManager profileManager = profilesManager.getProfile(request.primaryProfileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return profileManager.guardianManager()
                .generateTimeseries(request);
    }
}
