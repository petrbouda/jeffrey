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

import { ref, computed } from 'vue';
import AiAnalysisClient from '@/services/api/AiAnalysisClient';
import AiStatusResponse from '@/services/api/model/AiStatusResponse';
import AiAnalysisResponse from '@/services/api/model/AiAnalysisResponse';
import ChatMessage from '@/services/api/model/ChatMessage';

export interface AiAnalysisChatMessage extends ChatMessage {
    suggestions?: string[];
    toolsUsed?: string[];
    durationSeconds?: number;
}

/**
 * Composable for managing the AI Analysis assistant state and interactions.
 * Handles conversation history, message sending, and status management.
 */
export function useAiAnalysis(profileId: string) {
    const client = new AiAnalysisClient(profileId);

    // State
    const isLoading = ref(false);
    const error = ref<string | null>(null);
    const status = ref<AiStatusResponse | null>(null);
    const messages = ref<AiAnalysisChatMessage[]>([]);
    const currentInput = ref('');
    const canModify = ref(false);

    // Computed
    const isAvailable = computed(() => status.value?.available ?? false);

    const hasMessages = computed(() => messages.value.length > 0);

    const lastAssistantMessage = computed(() => {
        for (let i = messages.value.length - 1; i >= 0; i--) {
            if (messages.value[i].role === 'assistant') {
                return messages.value[i];
            }
        }
        return null;
    });

    /**
     * Check the AI analysis assistant status.
     */
    const checkStatus = async (): Promise<void> => {
        try {
            error.value = null;
            status.value = await client.getStatus();
        } catch (e: any) {
            error.value = e?.message || 'Failed to check AI status';
            status.value = { available: false, provider: null, model: null };
        }
    };

    /**
     * Send a message to the AI analysis assistant.
     * @param message - The user's message
     */
    const sendMessage = async (message: string): Promise<void> => {
        if (!message.trim() || isLoading.value) {
            return;
        }

        isLoading.value = true;
        error.value = null;

        // Add user message to history
        const userMessage: AiAnalysisChatMessage = {
            role: 'user',
            content: message.trim()
        };
        messages.value.push(userMessage);

        try {
            // Build history for API
            const history: ChatMessage[] = messages.value.slice(0, -1).map(m => ({
                role: m.role,
                content: m.content
            }));

            const response: AiAnalysisResponse = await client.chat(message.trim(), history, canModify.value);

            // Add assistant response to history
            const assistantMessage: AiAnalysisChatMessage = {
                role: 'assistant',
                content: response.content,
                suggestions: response.suggestions,
                toolsUsed: response.toolsUsed
            };
            messages.value.push(assistantMessage);
        } catch (e: any) {
            error.value = e?.response?.data || e?.message || 'Failed to get response from AI assistant';
            // Remove the user message if the request failed
            messages.value.pop();
        } finally {
            isLoading.value = false;
        }
    };

    /**
     * Clear the conversation history.
     */
    const clearHistory = (): void => {
        messages.value = [];
        error.value = null;
    };

    /**
     * Clear the current error.
     */
    const clearError = (): void => {
        error.value = null;
    };

    /**
     * Use a suggested follow-up as the next message.
     * @param followup - The follow-up suggestion to use
     */
    const useSuggestion = async (followup: string): Promise<void> => {
        currentInput.value = followup;
        await sendMessage(followup);
        currentInput.value = '';
    };

    return {
        // State
        isLoading,
        error,
        status,
        messages,
        currentInput,
        canModify,

        // Computed
        isAvailable,
        hasMessages,
        lastAssistantMessage,

        // Actions
        checkStatus,
        sendMessage,
        clearHistory,
        clearError,
        useSuggestion
    };
}
