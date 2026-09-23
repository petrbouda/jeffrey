/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.web.controllers.profile.pprof;

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
 * pprof-format differential flamegraph endpoints, serving the differential card grid at a pprof-specific
 * path so the UI can pick a client by format.
 */
@RestController
@RequestMapping("/api/internal/profiles/{primaryProfileId}/pprof/diff/{secondaryProfileId}/differential-flamegraph")
public class PprofDifferentialFlamegraphController {

    private static final Logger LOG = LoggerFactory.getLogger(PprofDifferentialFlamegraphController.class);

    private final ProfileManagerResolver resolver;
    private final StackSampleFlamegraphPanelProvider panelProvider;

    public PprofDifferentialFlamegraphController(ProfileManagerResolver resolver, StackSampleFlamegraphPanelProvider panelProvider) {
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
        LOG.debug("Listed pprof diff flamegraph panels: profileId={} count={}", primaryProfileId, panels.size());
        return panels;
    }
}
