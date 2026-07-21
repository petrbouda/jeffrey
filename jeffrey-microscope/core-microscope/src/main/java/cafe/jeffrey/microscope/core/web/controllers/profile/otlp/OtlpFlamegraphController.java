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
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.FlamegraphPanel;
import cafe.jeffrey.profile.panel.PanelContext;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;

import java.util.List;

/**
 * OTLP-format flamegraph endpoints. Serves the flamegraph card grid at an OTLP-specific path so the UI can
 * pick a client by format. Flamegraph generation itself stays on the shared generic endpoint (it is
 * format-agnostic — it takes an event-type code and reads the shared events table); only the panel
 * discovery differs, because the set of event types is format-specific.
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/otlp/flamegraph")
public class OtlpFlamegraphController {

    private static final Logger LOG = LoggerFactory.getLogger(OtlpFlamegraphController.class);

    private final ProfileManagerResolver resolver;
    private final StackSampleFlamegraphPanelProvider panelProvider;

    public OtlpFlamegraphController(ProfileManagerResolver resolver, StackSampleFlamegraphPanelProvider panelProvider) {
        this.resolver = resolver;
        this.panelProvider = panelProvider;
    }

    @GetMapping("/panels")
    public List<FlamegraphPanel> panels(@PathVariable("profileId") String profileId) {
        ProfileManager pm = resolver.resolve(profileId);
        List<FlamegraphPanel> panels = panelProvider.panels(pm.flamegraphManager().allEventSummaries(), PanelContext.PRIMARY);
        LOG.debug("Listed OTLP flamegraph panels: profileId={} count={}", profileId, panels.size());
        return panels;
    }
}
