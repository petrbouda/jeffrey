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
