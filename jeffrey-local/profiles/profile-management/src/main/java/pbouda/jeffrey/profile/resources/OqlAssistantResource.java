/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.ai.model.AiStatusResponse;
import pbouda.jeffrey.profile.ai.model.HeapDumpContext;
import pbouda.jeffrey.profile.ai.model.OqlChatRequest;
import pbouda.jeffrey.profile.ai.model.OqlChatResponse;
import pbouda.jeffrey.profile.ai.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.service.OqlAssistantService;
import pbouda.jeffrey.profile.heapdump.model.SortBy;
import pbouda.jeffrey.profile.manager.HeapDumpManager;

/**
 * REST resource for the AI-powered OQL assistant.
 * Provides endpoints for chat-based OQL query generation.
 */
public class OqlAssistantResource {

    private static final Logger LOG = LoggerFactory.getLogger(OqlAssistantResource.class);
    private static final int TOP_CLASSES_LIMIT = 50;

    private final HeapDumpManager heapDumpManager;
    private final OqlAssistantService assistantService;
    private final HeapDumpContextExtractor contextExtractor;

    public OqlAssistantResource(
            HeapDumpManager heapDumpManager,
            OqlAssistantService assistantService,
            HeapDumpContextExtractor contextExtractor) {
        this.heapDumpManager = heapDumpManager;
        this.assistantService = assistantService;
        this.contextExtractor = contextExtractor;
    }

    /**
     * Get the current status of the AI assistant.
     *
     * @return status information including provider and configuration state
     */
    @GET
    @Path("/status")
    public AiStatusResponse status() {
        LOG.debug("Checking OQL assistant status");
        return assistantService.getStatus();
    }

    /**
     * Send a chat message and get an OQL query response.
     *
     * @param request the chat request with message and history
     * @return the AI response with generated OQL
     */
    @POST
    @Path("/chat")
    public OqlChatResponse chat(OqlChatRequest request) {
        LOG.debug("OQL assistant chat request");
        // Extract heap context for the AI
        HeapDumpContext context = contextExtractor.extract(
                () -> heapDumpManager.getClassHistogram(TOP_CLASSES_LIMIT, SortBy.COUNT),
                () -> heapDumpManager.getClassHistogram(TOP_CLASSES_LIMIT, SortBy.SIZE),
                heapDumpManager::getSummary
        );

        return assistantService.chat(context, request);
    }
}
