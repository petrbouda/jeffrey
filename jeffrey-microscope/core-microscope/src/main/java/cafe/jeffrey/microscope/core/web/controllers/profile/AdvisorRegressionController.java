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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.advisor.regression.FrameDelta;
import cafe.jeffrey.profile.advisor.regression.ProfileComparison;
import cafe.jeffrey.profile.advisor.regression.RegressionService;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.util.List;

/**
 * Diffs this profile against a baseline profile and reports which frames moved.
 *
 * <p>No model is involved: the comparison is arithmetic over the two cached frame indexes, so it costs
 * nothing and returns immediately. The baseline is chosen with the same secondary-profile selector the
 * differential flamegraph uses, which is why it arrives as a query parameter rather than a body.</p>
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/advisor/regression")
public class AdvisorRegressionController {

    public record FrameDeltaResponse(
            String frame,
            double baselineSelfPct,
            double currentSelfPct,
            double deltaPp,
            boolean isNew) {

        static FrameDeltaResponse from(FrameDelta delta) {
            return new FrameDeltaResponse(
                    delta.name(),
                    delta.baselineSelfPct(),
                    delta.currentSelfPct(),
                    delta.deltaPp(),
                    delta.isNew());
        }
    }

    public record RegressionResponse(
            String eventType,
            double thresholdPp,
            List<FrameDeltaResponse> regressed,
            List<FrameDeltaResponse> improved,
            String promptFragment) {

        static RegressionResponse from(ProfileComparison comparison, String promptFragment) {
            return new RegressionResponse(
                    comparison.eventType(),
                    comparison.thresholdPp(),
                    comparison.regressed().stream().map(FrameDeltaResponse::from).toList(),
                    comparison.improved().stream().map(FrameDeltaResponse::from).toList(),
                    promptFragment);
        }
    }

    private final ProfileManagerResolver resolver;
    private final RegressionService regressionService;

    public AdvisorRegressionController(
            ProfileManagerResolver resolver, RegressionService regressionService) {

        this.resolver = resolver;
        this.regressionService = regressionService;
    }

    @GetMapping
    public RegressionResponse compare(
            @PathVariable("profileId") String profileId,
            @RequestParam("baselineProfileId") String baselineProfileId,
            @RequestParam("eventType") String eventType) {

        ProfileInfo current = resolver.resolve(profileId).info();
        ProfileInfo baseline = resolver.resolve(baselineProfileId).info();

        ProfileComparison comparison = regressionService.compare(baseline, current, eventType);
        return RegressionResponse.from(comparison, regressionService.promptFragment(comparison));
    }
}
