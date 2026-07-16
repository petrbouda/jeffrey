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

package cafe.jeffrey.profile.ai.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Base for MCP-backed AI analysis assistants (JFR analysis, heap dump analysis). Owns the shared
 * orchestration: availability/provider/model delegation to the {@link AiChatBackend}, the
 * tool-enabled analysis call, follow-up suggestion generation via {@link SuggestionRules}, and
 * error handling that converts any failure into {@link AssistantResponse#error(String)}.
 *
 * <p>Subclasses implement their domain-specific public {@code analyze} method by preparing a
 * {@link ToolExchange} (system prompt, history, contextual message, tool binding, span name) and
 * delegating to {@link #runAnalysis(String, ToolExchangePreparation)}. Preparation runs inside the
 * base error handling, so failures while resolving domain resources (e.g. a profile's DataSource)
 * are reported the same way as failures of the model call itself.</p>
 */
public abstract class McpAnalysisAssistantService implements AiAssistantService {

    private static final Logger LOG = LoggerFactory.getLogger(McpAnalysisAssistantService.class);

    private static final String ANALYSIS_FAILED_PREFIX = "Analysis failed: ";

    /**
     * Prepares the domain-specific {@link ToolExchange} for a single analysis call.
     */
    @FunctionalInterface
    public interface ToolExchangePreparation {

        /**
         * @return the fully assembled exchange for the tool-enabled model call
         * @throws Exception when domain resources for the call cannot be prepared
         */
        ToolExchange prepare() throws Exception;
    }

    private final String assistantName;
    private final AiChatBackend chatBackend;
    private final SuggestionRules suggestionRules;

    /**
     * @param assistantName   human-readable assistant name used in log statements
     * @param chatBackend     the provider-agnostic AI backend
     * @param suggestionRules the domain's follow-up suggestion rule table
     */
    protected McpAnalysisAssistantService(
            String assistantName,
            AiChatBackend chatBackend,
            SuggestionRules suggestionRules) {
        this.assistantName = assistantName;
        this.chatBackend = chatBackend;
        this.suggestionRules = suggestionRules;
        LOG.info("AI analysis assistant initialized: assistant={} provider={} model={}",
                assistantName, chatBackend.providerName(), chatBackend.modelName());
    }

    @Override
    public final boolean isAvailable() {
        return chatBackend.isAvailable();
    }

    @Override
    public final String getModelName() {
        return chatBackend.modelName();
    }

    @Override
    public final String getProviderName() {
        return chatBackend.providerName();
    }

    /**
     * Shared analysis orchestration: prepares the exchange, executes the tool-enabled call, and
     * enriches the raw result with follow-up suggestions. Any exception — thrown during
     * preparation or during the model call — is logged and converted into an error response.
     *
     * @param question    the user's original question, used for suggestion generation
     * @param preparation supplies the domain-specific {@link ToolExchange}
     * @return the assistant response, or an error response when the call fails
     */
    protected final AssistantResponse runAnalysis(String question, ToolExchangePreparation preparation) {
        try {
            ToolExchange exchange = preparation.prepare();
            ToolCallResult result = chatBackend.analyze(exchange);
            List<String> suggestions = suggestionRules.suggestFor(question, result.text());
            return new AssistantResponse(result.text(), suggestions, result.toolsUsed());
        } catch (Exception e) {
            LOG.error("Error during AI analysis: assistant={} message={}", assistantName, e.getMessage(), e);
            return AssistantResponse.error(ANALYSIS_FAILED_PREFIX + e.getMessage());
        }
    }
}
