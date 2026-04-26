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

package cafe.jeffrey.local.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/analysis")
public class AutoAnalysisController {

    private static final Logger LOG = LoggerFactory.getLogger(AutoAnalysisController.class);

    private final ProfileManagerResolver resolver;

    public AutoAnalysisController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public List<AutoAnalysisResult> list(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching auto analysis results");
        return resolver.resolve(profileId).autoAnalysisManager().analysisResults();
    }

    @PostMapping
    public List<AutoAnalysisResult> generate(@PathVariable("profileId") String profileId) {
        LOG.debug("Generating auto analysis on-demand");
        return resolver.resolve(profileId).autoAnalysisManager().generate();
    }
}
