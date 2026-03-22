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
 * No-operation implementation of OQL Assistant Service.
 * Used when AI is not configured or disabled.
 */
public class NoOpOqlAssistantService implements OqlAssistantService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public AiStatusResponse getStatus() {
        return AiStatusResponse.disabled();
    }

    @Override
    public OqlChatResponse chat(HeapDumpContext context, OqlChatRequest request) {
        return OqlChatResponse.textOnly(
                "AI assistant is not available. To enable it, configure an AI provider in application.properties."
        );
    }
}
