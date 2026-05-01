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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.guardian.guard.Guard.Category;
import cafe.jeffrey.profile.guardian.guard.GuardAnalysisResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/guardian")
public class GuardianController {

    public record GuardListResponse(String category, List<GuardAnalysisResult> results) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(GuardianController.class);

    private final ProfileManagerResolver resolver;

    public GuardianController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public List<GuardListResponse> list(@PathVariable("profileId") String profileId) {
        List<GuardAnalysisResult> guardAnalysisResults = resolver.resolve(profileId).guardianManager()
                .guardResults()
                .stream()
                .sorted(Comparator.comparing(a -> severityOrder(a.severity())))
                .toList();

        Map<Category, List<GuardAnalysisResult>> output = new TreeMap<>();
        for (GuardAnalysisResult result : guardAnalysisResults) {
            output.compute(result.category(), (category, results) -> {
                if (results != null) {
                    results.add(result);
                    return results;
                }
                List<GuardAnalysisResult> r = new ArrayList<>();
                r.add(result);
                return r;
            });
        }

        var result = output.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getKey().getOrder()))
                .map(e -> new GuardListResponse(e.getKey().getLabel(), e.getValue()))
                .toList();
        LOG.debug("Listed guardian results: profileId={} count={}", profileId, result.size());
        return result;
    }

    private static int severityOrder(AnalysisResult.Severity severity) {
        return switch (severity) {
            case WARNING -> 1;
            case OK -> 2;
            case INFO -> 3;
            case NA -> 4;
            case IGNORE -> 5;
        };
    }
}
