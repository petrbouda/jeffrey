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

package pbouda.jeffrey.profile.ai.service;

import pbouda.jeffrey.profile.ai.model.AiStatusResponse;
import pbouda.jeffrey.profile.ai.model.HeapDumpContext;
import pbouda.jeffrey.profile.ai.model.OqlChatRequest;
import pbouda.jeffrey.profile.ai.model.OqlChatResponse;

/**
 * Service for the AI-powered OQL assistant.
 * Handles conversation with AI to generate OQL queries from natural language.
 */
public interface OqlAssistantService {

    /**
     * Check if the AI assistant is available and properly configured.
     *
     * @return true if the assistant can be used
     */
    boolean isAvailable();

    /**
     * Get the current status of the AI assistant.
     *
     * @return status information including provider and configuration state
     */
    AiStatusResponse getStatus();

    /**
     * Process a chat message and generate an OQL response.
     *
     * @param context the heap dump context for AI to reference
     * @param request the chat request with message and history
     * @return the AI response with generated OQL
     */
    OqlChatResponse chat(HeapDumpContext context, OqlChatRequest request);
}
