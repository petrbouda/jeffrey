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
import cafe.jeffrey.microscope.persistence.api.AdvisorClaimIndexRepository;
import cafe.jeffrey.microscope.persistence.api.AdvisorClaimIndexRow;
import cafe.jeffrey.profile.advisor.mcp.SourceToolsRegistry;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPrompt;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPromptManager;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPromptType;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettings;
import cafe.jeffrey.profile.advisor.source.SourceAnalysisTools;
import cafe.jeffrey.profile.advisor.source.SourceTree;
import cafe.jeffrey.profile.advisor.source.SourceTreeResolver;
import cafe.jeffrey.profile.advisor.verify.PatchVerification;
import cafe.jeffrey.profile.advisor.verify.PatchVerifier;
import cafe.jeffrey.profile.common.pipeline.PipelineRun;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.McpToolsetFactory;
import cafe.jeffrey.profile.ai.chat.ToolBinding;
import cafe.jeffrey.profile.ai.chat.ToolCallResult;
import cafe.jeffrey.profile.ai.chat.ToolExchange;
import cafe.jeffrey.provider.profile.api.AdvisorClaimRow;
import cafe.jeffrey.provider.profile.api.AdvisorRecommendationRow;
import cafe.jeffrey.provider.profile.api.ProfileAdvisorRepository;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.Severity;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Produces grounded, verified recommendations for one profile.
 *
 * <p>The shape is the standalone analyst's, with the clone replaced by a folder the user already has:
 * resolve the cached prompt, point the read-only tools at the source tree, let the model explore it the
 * way an agentic code assistant would, then check everything it said before storing any of it.</p>
 *
 * <p>The ordering of that checking is deliberate. Cited frames are resolved against the measured call
 * tree, cited paths against the working tree, and the patch against {@code git apply} — all before
 * severity is computed, so severity can be derived from what survived. A claim the model invented
 * therefore cannot raise a profile's priority, which is the difference between a ranking you can act on
 * and a ranking that rewards confident writing.</p>
 */
public class AdvisorService {

    private static final Logger LOG = LoggerFactory.getLogger(AdvisorService.class);

    private static final String SPAN_NAME = "advisor.recommendation";

    private final AdvisorPromptManager promptManager;
    private final AdvisorSettings settings;
    private final SourceTreeResolver sourceTreeResolver;
    private final AiChatBackend aiChatBackend;
    private final SourceToolsRegistry sourceToolsRegistry;
    private final McpToolsetFactory mcpToolsetFactory;
    private final ProfileAdvisorRepository advisorRepository;
    private final AdvisorClaimIndexRepository claimIndexRepository;
    private final PatchVerifier patchVerifier;
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
            AdvisorClaimIndexRepository claimIndexRepository,
            PatchVerifier patchVerifier,
            String recordingId,
            Clock clock) {

        this.promptManager = promptManager;
        this.settings = settings;
        this.sourceTreeResolver = sourceTreeResolver;
        this.aiChatBackend = aiChatBackend;
        this.sourceToolsRegistry = sourceToolsRegistry;
        this.mcpToolsetFactory = mcpToolsetFactory;
        this.advisorRepository = advisorRepository;
        this.claimIndexRepository = claimIndexRepository;
        this.patchVerifier = patchVerifier;
        this.recordingId = recordingId;
        this.clock = clock;
    }

    /**
     * Runs the full pipeline for {@code target}, timing each stage on {@code run}, and stores what
     * survived verification.
     *
     * <p>Each phase is a stage rather than a status flag, which is what lets the UI show where a
     * five-minute run's time actually went. The interesting number is usually {@code analyze}, and
     * before this it was invisible.</p>
     */
    public AdvisorResult generate(AdvisorTarget target, PipelineRun run) {
        AdvisorPromptType promptType = AdvisorPromptType.byEventCode(target.eventType())
                .orElseThrow(() -> Exceptions.invalidRequest(
                        "The Advisor does not analyze this event type: " + target.eventType()));

        // Each stage assigns into a holder rather than returning, because runStage owns the timing and a
        // stage that also produced the value would have to smuggle it out of the lambda anyway.
        AdvisorRunContext context = new AdvisorRunContext();

        run.runStage(AdvisorStages.PROMPT, () ->
                context.prompt = promptManager.resolve(promptType, settings.pruneThresholdPct()));

        run.runStage(AdvisorStages.SOURCE, () ->
                context.sourceTree = sourceTreeResolver.resolve(settings.sourcePath(), recordingId));

        run.runStage(AdvisorStages.ANALYZE, () ->
                context.raw = analyze(
                        target, context.prompt, new SourceAnalysisTools(context.sourceTree.root())));

        run.runStage(AdvisorStages.VERIFY, () -> context.result = verify(context));

        run.runStage(AdvisorStages.STORE, () -> store(target, context.result, context.sourceTree));

        AdvisorResult result = context.result;
        LOG.info("Generated advisor recommendations: profile_id={} event_type={} severity={} claims={} "
                        + "grounded={} has_patch={} patch_applies={}",
                target.profileId(), target.eventType(), result.severity(), result.claims().size(),
                result.groundedClaimCount(), result.hasPatch(), result.verification().applies());
        return result;
    }

    /**
     * Checks everything the model said before any of it is stored: cited frames against the measured
     * call tree, cited paths against the working copy, the patch against {@code git apply}. Severity is
     * computed last, from what survived, so an unverifiable claim cannot raise a profile's priority.
     */
    private AdvisorResult verify(AdvisorRunContext context) {
        AdvisorOutputParser.ParsedOutput parsed = AdvisorOutputParser.parse(context.raw.text());

        List<GroundedClaim> claims =
                new ClaimGrounder(context.prompt.frameIndex(), context.sourceTree.root())
                        .ground(parsed.claims());
        Severity severity = SeverityCalculator.fromGroundedClaims(claims);

        PatchVerification verification = patchVerifier.verify(
                parsed.patch(), context.sourceTree.root(), context.sourceTree.resolvedRef());

        return new AdvisorResult(
                severity, parsed.recommendations(), parsed.patch(), claims, verification,
                context.raw.usage());
    }

    /** What one run accumulates as its stages complete. */
    private static final class AdvisorRunContext {

        private AdvisorPrompt prompt;
        private SourceTree sourceTree;
        private ToolCallResult raw;
        private AdvisorResult result;
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
                    AdvisorPrompts.SYSTEM_PROMPT,
                    null,
                    AdvisorPrompts.userMessage(
                            prompt.label(),
                            prompt.markdown(),
                            ProfileFactsBuilder.build(prompt.frameIndex())),
                    toolBinding,
                    SPAN_NAME);
            return aiChatBackend.analyze(exchange);
        } finally {
            sourceToolsRegistry.unregister(runId);
        }
    }

    private void store(AdvisorTarget target, AdvisorResult result, SourceTree sourceTree) {
        Instant generatedAt = clock.instant();

        advisorRepository.upsertRecommendation(new AdvisorRecommendationRow(
                target.eventType(),
                result.severity().name(),
                result.recommendations(),
                result.patch(),
                Json.toString(result.verification()),
                sourceTree.resolvedRef(),
                result.usage().inputTokens(),
                result.usage().outputTokens(),
                result.usage().costUsd(),
                generatedAt));

        advisorRepository.replaceClaims(target.eventType(), toClaimRows(target, result, generatedAt));
        indexGroundedClaims(target, result, generatedAt);
    }

    private static List<AdvisorClaimRow> toClaimRows(
            AdvisorTarget target, AdvisorResult result, Instant generatedAt) {

        return result.claims().stream()
                .map(claim -> new AdvisorClaimRow(
                        target.eventType(),
                        claim.claim().title(),
                        citedFrameOf(claim),
                        claim.claim().sourcePath(),
                        claim.grounded(),
                        claim.sourceFound(),
                        claim.grounded() ? claim.frame().selfPct() : 0.0,
                        claim.grounded() ? claim.frame().totalPct() : 0.0,
                        generatedAt))
                .toList();
    }

    /**
     * Copies the grounded claims into the cross-profile index. Ungrounded ones are deliberately left
     * behind: they belong on the profile that produced them, where the reader can see they were not
     * substantiated, but counting them in a fleet rollup would let one invented frame look like a
     * fleet-wide pattern. A profile with no project is skipped for the same reason — there is nothing
     * to group it by.
     */
    private void indexGroundedClaims(AdvisorTarget target, AdvisorResult result, Instant generatedAt) {
        if (!target.hasProject()) {
            return;
        }

        List<AdvisorClaimIndexRow> rows = result.claims().stream()
                .filter(GroundedClaim::grounded)
                .map(claim -> new AdvisorClaimIndexRow(
                        target.profileId(),
                        target.profileName(),
                        target.workspaceId(),
                        target.projectId(),
                        target.eventType(),
                        claim.claim().title(),
                        claim.frame().name(),
                        claim.claim().sourcePath(),
                        claim.frame().selfPct(),
                        claim.frame().totalPct(),
                        generatedAt))
                .toList();

        claimIndexRepository.replaceForProfile(target.profileId(), target.eventType(), rows);
    }

    /**
     * A grounded claim is stored under the frame's measured spelling, not the model's. The model may
     * have written {@code RateTable#lookup} for a frame the profile calls
     * {@code com/acme/RateTable.lookup}; storing the measured name is what lets the fleet rollup group
     * two projects that cited the same frame differently.
     */
    private static String citedFrameOf(GroundedClaim claim) {
        return claim.grounded() ? claim.frame().name() : claim.claim().citedFrame();
    }
}
