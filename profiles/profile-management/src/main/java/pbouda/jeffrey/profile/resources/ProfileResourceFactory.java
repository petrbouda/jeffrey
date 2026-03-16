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

package pbouda.jeffrey.profile.resources;

import pbouda.jeffrey.profile.ai.heapmcp.service.HeapDumpAnalysisAssistantService;
import pbouda.jeffrey.profile.ai.mcp.service.JfrAnalysisAssistantService;
import pbouda.jeffrey.profile.ai.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.service.OqlAssistantService;
import pbouda.jeffrey.profile.manager.ProfileManager;

public class ProfileResourceFactory {

    private final OqlAssistantService oqlAssistantService;
    private final JfrAnalysisAssistantService jfrAnalysisAssistantService;
    private final HeapDumpContextExtractor heapDumpContextExtractor;
    private final HeapDumpAnalysisAssistantService heapDumpAnalysisAssistantService;

    public ProfileResourceFactory(
            OqlAssistantService oqlAssistantService,
            JfrAnalysisAssistantService jfrAnalysisAssistantService,
            HeapDumpContextExtractor heapDumpContextExtractor,
            HeapDumpAnalysisAssistantService heapDumpAnalysisAssistantService) {
        this.oqlAssistantService = oqlAssistantService;
        this.jfrAnalysisAssistantService = jfrAnalysisAssistantService;
        this.heapDumpContextExtractor = heapDumpContextExtractor;
        this.heapDumpAnalysisAssistantService = heapDumpAnalysisAssistantService;
    }

    public ProfileResource create(ProfileManager profileManager) {
        return new ProfileResource(
                profileManager,
                oqlAssistantService,
                jfrAnalysisAssistantService,
                heapDumpContextExtractor,
                heapDumpAnalysisAssistantService);
    }
}
