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

package cafe.jeffrey.microscope.core.web.controllers.profile.otlp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.FlamegraphPanel;
import cafe.jeffrey.profile.panel.PanelContext;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;

import java.util.List;

/**
 * OTLP-format differential flamegraph endpoints, serving the differential card grid at an OTLP-specific
 * path so the UI can pick a client by format.
 */
@RestController
@RequestMapping("/api/internal/profiles/{primaryProfileId}/otlp/diff/{secondaryProfileId}/differential-flamegraph")
public class OtlpDifferentialFlamegraphController {

    private static final Logger LOG = LoggerFactory.getLogger(OtlpDifferentialFlamegraphController.class);

    private final ProfileManagerResolver resolver;
    private final StackSampleFlamegraphPanelProvider panelProvider;

    public OtlpDifferentialFlamegraphController(ProfileManagerResolver resolver, StackSampleFlamegraphPanelProvider panelProvider) {
        this.resolver = resolver;
        this.panelProvider = panelProvider;
    }

    @GetMapping("/panels")
    public List<FlamegraphPanel> panels(
            @PathVariable("primaryProfileId") String primaryProfileId,
            @PathVariable("secondaryProfileId") String secondaryProfileId) {
        ProfileManager primary = resolver.resolve(primaryProfileId);
        ProfileManager secondary = resolver.resolve(secondaryProfileId);
        FlamegraphManager diffManager = primary.diffFlamegraphManager(secondary);
        List<FlamegraphPanel> panels = panelProvider.panels(diffManager.allEventSummaries(), PanelContext.DIFFERENTIAL);
        LOG.debug("Listed OTLP diff flamegraph panels: profileId={} count={}", primaryProfileId, panels.size());
        return panels;
    }
}
