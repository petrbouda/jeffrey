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

package cafe.jeffrey.profile.advisor.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.advisor.mcp.SourceToolsRegistry;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPrompt;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPromptManager;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPromptType;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPrompts;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettings;
import cafe.jeffrey.profile.advisor.source.SourceAnalysisTools;
import cafe.jeffrey.profile.advisor.source.SourceTree;
import cafe.jeffrey.profile.advisor.source.SourceTreeResolver;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.McpToolsetFactory;
import cafe.jeffrey.profile.ai.chat.ToolBinding;
import cafe.jeffrey.profile.ai.chat.ToolCallResult;
import cafe.jeffrey.profile.ai.chat.ToolExchange;
import cafe.jeffrey.provider.profile.api.AdvisorRecommendationRow;
import cafe.jeffrey.provider.profile.api.ProfileAdvisorRepository;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.time.Clock;
import java.util.concurrent.CancellationException;

/**
 * Produces recommendations and a proposed patch for one profile.
 *
 * <p>The shape is the standalone analyst's, with the clone replaced by a folder the user already has:
 * resolve the cached prompt, point the read-only tools at the source tree, let the model explore it the
 * way an agentic code assistant would, and build its proposed diff into an applicable patch — each
 * phase reported through the sink, so the run timeline shows where the time went. One phase per
 * artifact produced: prompt, recommendation, patch.</p>
 *
 */
public final class AdvisorService {

    private static final Logger LOG = LoggerFactory.getLogger(AdvisorService.class);

    private static final String SPAN_NAME = "advisor.recommendation";

    private static final String CANCELLED_MESSAGE = "The run was cancelled";

    private final AdvisorPromptManager promptManager;
    private final AdvisorSettings settings;
    private final SourceTreeResolver sourceTreeResolver;
    private final AiChatBackend aiChatBackend;
    private final SourceToolsRegistry sourceToolsRegistry;
    private final McpToolsetFactory mcpToolsetFactory;
    private final ProfileAdvisorRepository advisorRepository;
    private final String recordingId;
    private final Clock clock;

    public AdvisorService(
            AdvisorPromptManager promptManager,
            AdvisorSettings settings,
            SourceTreeResolver sourceTreeResolver,
            AiChatBackend aiChatBackend,
            SourceToolsRegistry sourceToolsRegistry,
            McpToolsetFactory mcpToolsetFactory,
            ProfileAdvisorRepository advisorRepository,
            String recordingId,
            Clock clock) {

        this.promptManager = promptManager;
        this.settings = settings;
        this.sourceTreeResolver = sourceTreeResolver;
        this.aiChatBackend = aiChatBackend;
        this.sourceToolsRegistry = sourceToolsRegistry;
        this.mcpToolsetFactory = mcpToolsetFactory;
        this.advisorRepository = advisorRepository;
        this.recordingId = recordingId;
        this.clock = clock;
    }

    /**
     * Validates the configured source folder and pairs it with the commit information the result is
     * pinned to. Deliberately separate from {@link #generate}: the folder belongs to the batch, not to
     * one event type, so it is resolved once before any run starts rather than re-validated — and
     * re-parsed out of {@code .git} — by every type in the fan-out.
     *
     * @throws cafe.jeffrey.shared.common.exception.JeffreyClientException when the folder is unusable,
     *         which refuses the launch outright instead of failing every type the same way
     */
    public SourceTree resolveSource() {
        return sourceTreeResolver.resolve(settings.sourcePath(), recordingId);
    }

    /**
     * Runs the full pipeline for {@code target} against an already-resolved {@code sourceTree},
     * reporting each phase through {@code sink}, and stores the recommendation and patch it produced.
     */
    public AdvisorResult generate(AdvisorTarget target, AdvisorProgressSink sink, SourceTree sourceTree) {
        AdvisorPromptType promptType = AdvisorPromptType.byEventCode(target.eventType())
                .orElseThrow(() -> Exceptions.invalidRequest(
                        "The Advisor does not analyze this event type: " + target.eventType()));

        abortIfEnded(sink);
        sink.preparingPrompt();
        AdvisorPrompt prompt = promptManager.resolve(promptType, settings.pruneThresholdPct());

        abortIfEnded(sink);
        sink.recommending();
        ToolCallResult raw = analyze(target, prompt, new SourceAnalysisTools(sourceTree.root()));
        AdvisorOutputParser.ParsedOutput parsed = AdvisorOutputParser.parse(raw.text());

        abortIfEnded(sink);
        sink.buildingPatch();
        String patch = new PatchBuilder(sourceTree.root()).build(parsed.patch());

        AdvisorResult result = new AdvisorResult(parsed.recommendation(), patch);

        abortIfEnded(sink);
        store(target, result, sourceTree);

        LOG.info("Generated advisor recommendations: profile_id={} event_type={} patched={}",
                target.profileId(), target.eventType(), result.hasPatch());
        return result;
    }

    /**
     * Stops the pipeline at a phase boundary when the run has already ended.
     *
     * <p>Cancelling marks the run failed and interrupts this thread, but an AI call already in flight
     * can swallow the interrupt and return normally — at which point, without this, the remaining
     * phases would build the patch and <em>store the results</em> for a run the user ended. Checking at
     * each boundary keeps the cost of a late cancel to the phase already in flight.</p>
     */
    private static void abortIfEnded(AdvisorProgressSink sink) {
        if (sink.ended()) {
            throw new CancellationException(CANCELLED_MESSAGE);
        }
    }

    /**
     * Runs the configured AI backend over the source tree, exposing the tools both in-process (Spring AI
     * providers) and over the run-scoped MCP endpoint (the Claude Code CLI). The {@code runId} binds the
     * tools only for the duration of this call, so a CLI that calls back later finds nothing.
     */
    private ToolCallResult analyze(AdvisorTarget target, AdvisorPrompt prompt, SourceAnalysisTools tools) {
        String runId = IDGenerator.generate();
        sourceToolsRegistry.register(runId, tools);
        try {
            ToolBinding toolBinding =
                    new ToolBinding(tools, mcpToolsetFactory.forAdvisorRun(target.profileId(), runId));
            ToolExchange exchange = new ToolExchange(
                    AdvisorPrompts.SYSTEM_PROMPT, null, prompt.prompt(), toolBinding, SPAN_NAME);
            return aiChatBackend.analyze(exchange);
        } finally {
            sourceToolsRegistry.unregister(runId);
        }
    }

    /**
     * Stores what the run produced, pinned to the source revision it was produced against — the model's
     * prose is only true of the code it read.
     */
    private void store(AdvisorTarget target, AdvisorResult result, SourceTree sourceTree) {
        advisorRepository.upsertRecommendation(new AdvisorRecommendationRow(
                target.eventType(),
                result.recommendation(),
                result.patch(),
                sourceTree.resolvedRef(),
                clock.instant()));
    }
}
