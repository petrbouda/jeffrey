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

import BaseProfileClient from '@/services/api/BaseProfileClient';
import AiStatusResponse from '@/services/api/model/AiStatusResponse';
import AiAnalysisResponse from '@/services/api/model/AiAnalysisResponse';
import ChatMessage from '@/services/api/model/ChatMessage';

/**
 * API client for the AI-powered JFR analysis assistant.
 * Provides methods for checking AI status and sending chat messages for JFR analysis.
 */
export default class AiAnalysisClient extends BaseProfileClient {

    constructor(profileId: string) {
        super(profileId, 'ai-analysis');
    }

    /**
     * Get the current status of the AI analysis assistant.
     * @returns Promise resolving to the AI status including availability and provider
     */
    public getStatus(): Promise<AiStatusResponse> {
        return this.get<AiStatusResponse>('/status');
    }

    /**
     * Send a chat message to get an AI analysis response.
     * @param message - The user's natural language message
     * @param history - The conversation history for context
     * @returns Promise resolving to the AI response with analysis results
     */
    public chat(message: string, history: ChatMessage[]): Promise<AiAnalysisResponse> {
        return this.post<AiAnalysisResponse>('/chat', { message, history });
    }
}
