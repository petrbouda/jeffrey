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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.ai.oql.model.AiStatusResponse;
import cafe.jeffrey.profile.ai.oql.model.HeapDumpContext;
import cafe.jeffrey.profile.ai.oql.model.OqlChatRequest;
import cafe.jeffrey.profile.ai.oql.model.OqlChatResponse;
import cafe.jeffrey.profile.ai.oql.service.HeapDumpContextExtractor;
import cafe.jeffrey.profile.ai.oql.service.OqlAssistantService;
import cafe.jeffrey.profile.heapdump.model.SortBy;
import cafe.jeffrey.profile.manager.HeapDumpManager;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/heap/oql-assistant")
public class OqlAssistantController {

    private static final Logger LOG = LoggerFactory.getLogger(OqlAssistantController.class);
    private static final int TOP_CLASSES_LIMIT = 50;

    private final ProfileManagerResolver resolver;
    private final OqlAssistantService assistantService;
    private final HeapDumpContextExtractor contextExtractor;

    public OqlAssistantController(
            ProfileManagerResolver resolver,
            OqlAssistantService assistantService,
            HeapDumpContextExtractor contextExtractor) {
        this.resolver = resolver;
        this.assistantService = assistantService;
        this.contextExtractor = contextExtractor;
    }

    @GetMapping("/status")
    public AiStatusResponse status() {
        LOG.debug("Checking OQL assistant status");
        return assistantService.getStatus();
    }

    @PostMapping("/chat")
    public OqlChatResponse chat(
            @PathVariable("profileId") String profileId,
            @RequestBody OqlChatRequest request) {
        LOG.debug("OQL assistant chat request");
        HeapDumpManager heapDumpManager = resolver.resolve(profileId).heapDumpManager();
        HeapDumpContext context = contextExtractor.extract(
                () -> heapDumpManager.getClassHistogram(TOP_CLASSES_LIMIT, SortBy.COUNT),
                () -> heapDumpManager.getClassHistogram(TOP_CLASSES_LIMIT, SortBy.SIZE),
                heapDumpManager::getSummary);
        return assistantService.chat(context, request);
    }
}
