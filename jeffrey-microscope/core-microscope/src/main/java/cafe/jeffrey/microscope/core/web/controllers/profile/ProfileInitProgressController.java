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
package cafe.jeffrey.microscope.core.web.controllers.profile;

import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * How far the parsing of a profile's recording has got.
 * <p>
 * The profile-init pipeline has recorded its stages since it was introduced, but nothing served them:
 * the recordings list embeds a summary of them per row, and there was no way to ask about one profile.
 * That was enough while the only caller was a page listing every recording, and stopped being enough
 * once ingestion could be started from outside the UI — a caller that starts an analysis and gets a
 * profile id back needs somewhere to watch it, the way the heap dump's own initialization already
 * offers.
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}")
public class ProfileInitProgressController {

    private final PipelineRunRegistry<String> profileInitRunRegistry;

    public ProfileInitProgressController(PipelineRunRegistry<String> profileInitRunRegistry) {
        this.profileInitRunRegistry = profileInitRunRegistry;
    }

    /**
     * The stages of the run for this profile — the current one, the last finished one, or an idle
     * report when this process has not run it. Idle rather than 404: a profile parsed before a restart
     * is a real profile whose run is simply no longer in memory, which is not the same as not existing.
     */
    @GetMapping("/init-progress")
    public PipelineProgress initProgress(@PathVariable("profileId") String profileId) {
        return profileInitRunRegistry.progress(profileId);
    }
}
