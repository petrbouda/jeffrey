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

package cafe.jeffrey.profile.ai.duckdb.heapdump.service;

import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.AssistantResponse;
import cafe.jeffrey.profile.ai.chat.McpAnalysisAssistantService;
import cafe.jeffrey.profile.ai.chat.McpToolset;
import cafe.jeffrey.profile.ai.chat.McpToolsetFactory;
import cafe.jeffrey.profile.ai.chat.SuggestionRules;
import cafe.jeffrey.profile.ai.chat.SuggestionRules.QuestionRule;
import cafe.jeffrey.profile.ai.chat.SuggestionRules.ResponseRule;
import cafe.jeffrey.profile.ai.chat.ToolBinding;
import cafe.jeffrey.profile.ai.chat.ToolExchange;
import cafe.jeffrey.profile.ai.duckdb.heapdump.model.HeapDumpAnalysisRequest;
import cafe.jeffrey.profile.ai.duckdb.heapdump.prompt.HeapDumpAnalysisSystemPrompt;
import cafe.jeffrey.profile.ai.duckdb.heapdump.tools.HeapDumpMcpTools;
import cafe.jeffrey.profile.ai.duckdb.heapdump.tools.HeapDumpToolsDelegate;

import java.util.List;
import java.util.Set;

/**
 * Implementation of heap dump analysis assistant over the provider-agnostic {@link AiChatBackend}.
 */
public class HeapDumpAnalysisAssistantServiceImpl extends McpAnalysisAssistantService
        implements HeapDumpAnalysisAssistantService {

    private static final String ASSISTANT_NAME = "Heap Dump Analysis Assistant";

    private static final String AI_CALL_SPAN_NAME = "ai.heapdump-analysis.call";

    private static final SuggestionRules SUGGESTION_RULES = new SuggestionRules(
            List.of(
                    new QuestionRule(Set.of("summary", "overview"), List.of(
                            "Show me the class histogram by size",
                            "Are there any leak suspects?")),
                    new QuestionRule(Set.of("histogram", "class"), List.of(
                            "Show me the biggest objects by retained size",
                            "Browse instances of the largest class")),
                    new QuestionRule(Set.of("leak", "suspect"), List.of(
                            "Show the dominator tree roots",
                            "What are the biggest objects?")),
                    new QuestionRule(Set.of("string"), List.of(
                            "Analyze collection fill ratios",
                            "Show the class histogram"))),
            List.of(
                    new ResponseRule("retained", "dominator", List.of(
                            "Show the dominator tree for more retained size details"))),
            List.of(
                    "Show me the heap summary",
                    "What are the top memory consumers?",
                    "Are there potential memory leaks?"));

    private final McpToolsetFactory mcpToolsetFactory;

    public HeapDumpAnalysisAssistantServiceImpl(
            AiChatBackend chatBackend,
            McpToolsetFactory mcpToolsetFactory) {
        super(ASSISTANT_NAME, chatBackend, SUGGESTION_RULES);
        this.mcpToolsetFactory = mcpToolsetFactory;
    }

    @Override
    public AssistantResponse analyze(HeapDumpToolsDelegate delegate, HeapDumpAnalysisRequest request) {
        return runAnalysis(request.message(), () -> {
            HeapDumpMcpTools tools = new HeapDumpMcpTools(delegate);
            McpToolset mcpToolset = mcpToolsetFactory.forHeap(request.profileId());
            ToolBinding toolBinding = new ToolBinding(tools, mcpToolset);

            return new ToolExchange(
                    HeapDumpAnalysisSystemPrompt.SYSTEM_PROMPT,
                    request.history(),
                    request.message(),
                    toolBinding,
                    AI_CALL_SPAN_NAME);
        });
    }
}
