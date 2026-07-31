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

package cafe.jeffrey.performance.analyst.recommendations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.performance.analyst.flamegraph.FlamegraphAiPrompt;
import cafe.jeffrey.performance.analyst.flamegraph.RecordingAiPromptManager;
import cafe.jeffrey.performance.analyst.mcp.RepoToolsRegistry;
import cafe.jeffrey.performance.analyst.mcp.RepoToolsetFactory;
import cafe.jeffrey.performance.analyst.persistence.GeneratedRecommendation;
import cafe.jeffrey.performance.analyst.persistence.GeneratedRecommendationRepository;
import cafe.jeffrey.performance.analyst.persistence.RecommendationClaimRepository;
import cafe.jeffrey.performance.analyst.persistence.StoredClaim;
import cafe.jeffrey.performance.analyst.persistence.VersionControlSystem;
import cafe.jeffrey.performance.analyst.verification.PatchVerification;
import cafe.jeffrey.performance.analyst.verification.PatchVerifier;
import cafe.jeffrey.performance.analyst.versioncontrolsystem.VersionControlSystemCredentials;
import cafe.jeffrey.performance.analyst.versioncontrolsystem.VersionControlSystemManager;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.ToolBinding;
import cafe.jeffrey.profile.ai.chat.ToolCallResult;
import cafe.jeffrey.profile.ai.chat.ToolExchange;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.Severity;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Generates repository-aware AI recommendations for a recording. It resolves the project's configured
 * repository and the cached flamegraph prompt for the requested event type, clones the repository, then
 * runs an AI analysis with read-only {@link RepoAnalysisTools} over the checkout — the same way an
 * agentic code assistant explores a codebase. The clone is always deleted afterwards.
 *
 * <p>Everything the model produces is checked before it is stored, and all of that checking happens
 * inside the window where the checkout still exists: cited frames are resolved against the measured
 * profile, cited paths against the working tree, and the patch against {@code git apply}. Severity is
 * then computed from what survived, so an unverifiable claim cannot raise a profile's priority.</p>
 */
public class RecordingRecommendationManager {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingRecommendationManager.class);

    private static final String SPAN_NAME = "performance-analyst.recommendation";

    private final VersionControlSystemManager versionControlSystemManager;
    private final RecordingAiPromptManager promptManager;
    private final RepositoryCloner repositoryCloner;
    private final AiChatBackend aiChatBackend;
    private final RepoToolsRegistry repoToolsRegistry;
    private final RepoToolsetFactory repoToolsetFactory;
    private final GeneratedRecommendationRepository recommendationRepository;
    private final RecommendationClaimRepository claimRepository;
    private final PatchVerifier patchVerifier;
    private final Clock clock;

    public RecordingRecommendationManager(
            VersionControlSystemManager versionControlSystemManager,
            RecordingAiPromptManager promptManager,
            RepositoryCloner repositoryCloner,
            AiChatBackend aiChatBackend,
            RepoToolsRegistry repoToolsRegistry,
            RepoToolsetFactory repoToolsetFactory,
            GeneratedRecommendationRepository recommendationRepository,
            RecommendationClaimRepository claimRepository,
            PatchVerifier patchVerifier,
            Clock clock) {
        this.versionControlSystemManager = versionControlSystemManager;
        this.promptManager = promptManager;
        this.repositoryCloner = repositoryCloner;
        this.aiChatBackend = aiChatBackend;
        this.repoToolsRegistry = repoToolsRegistry;
        this.repoToolsetFactory = repoToolsetFactory;
        this.recommendationRepository = recommendationRepository;
        this.claimRepository = claimRepository;
        this.patchVerifier = patchVerifier;
        this.clock = clock;
    }

    /**
     * Produces the recommendations for {@code target}'s event type against the repository configured for
     * its project. Reports the clone/analyze/verify phases through {@code sink}. Throws
     * {@link cafe.jeffrey.shared.common.exception.JeffreyClientException} when no repository is
     * configured or the recording has no matching prompt.
     */
    public RecommendationResult generate(RecommendationTarget target, RecommendationProgressSink sink) {
        String projectId = target.projectId();
        String recordingId = target.recordingId();
        String eventType = target.eventType();

        VersionControlSystem vcs = versionControlSystemManager.find(projectId)
                .orElseThrow(() -> Exceptions.invalidRequest(
                        "No repository configured for this project. Configure it under Version Control System."));

        FlamegraphAiPrompt prompt = resolvePrompt(recordingId, eventType, projectId);
        String token = resolveToken(vcs);

        sink.cloning();
        try (ClonedRepository repository = repositoryCloner.clone(vcs.url(), token, vcs.platform(), target.commitRef())) {
            sink.analyzing();
            ToolCallResult raw = analyze(prompt, new RepoAnalysisTools(repository.root()));

            RecommendationOutputParser.ParsedOutput parsed = RecommendationOutputParser.parse(raw.text());

            List<GroundedClaim> claims = new ClaimGrounder(prompt.frameIndex(), repository.root())
                    .ground(parsed.claims());
            Severity severity = SeverityCalculator.fromGroundedClaims(claims);

            sink.verifying();
            PatchVerification verification =
                    patchVerifier.verify(parsed.patch(), repository.root(), repository.resolvedRef());

            RecommendationResult result = new RecommendationResult(
                    severity, parsed.recommendations(), parsed.patch(), claims, verification, raw.usage());

            store(target, result);

            LOG.info("Generated AI recommendations: project_id={} recording_id={} event_type={} severity={} "
                            + "claims={} grounded={} has_patch={} patch_applies={}",
                    projectId, recordingId, eventType, severity, claims.size(), result.groundedClaimCount(),
                    result.hasPatch(), verification.applies());
            return result;
        }
    }

    /**
     * Runs the configured AI backend over the cloned checkout, exposing {@code tools} both in-process
     * (Spring AI providers) and over the run-scoped MCP endpoint (the Claude Code CLI). The {@code runId}
     * binds the tools in {@link RepoToolsRegistry} only for the duration of this call, since the clone is
     * deleted when the caller's try-with-resources closes.
     */
    private ToolCallResult analyze(FlamegraphAiPrompt prompt, RepoAnalysisTools tools) {
        String runId = UUID.randomUUID().toString();
        repoToolsRegistry.register(runId, tools);
        try {
            ToolBinding toolBinding = new ToolBinding(tools, repoToolsetFactory.forRun(runId));
            ToolExchange exchange = new ToolExchange(
                    RecommendationPrompts.SYSTEM_PROMPT,
                    null,
                    RecommendationPrompts.userMessage(
                            prompt.label(),
                            prompt.markdown(),
                            ProfileFactsBuilder.build(prompt.frameIndex())),
                    toolBinding,
                    SPAN_NAME);
            return aiChatBackend.analyze(exchange);
        } finally {
            repoToolsRegistry.unregister(runId);
        }
    }

    private void store(RecommendationTarget target, RecommendationResult result) {
        Instant generatedAt = clock.instant();

        recommendationRepository.upsert(new GeneratedRecommendation(
                target.recordingId(), target.eventType(), target.hubId(), target.workspaceId(),
                target.projectId(), target.projectName(), result.severity(), result.recommendations(),
                result.patch(), result.verification(), result.usage(), generatedAt));

        claimRepository.replaceForRecording(
                target.recordingId(), target.eventType(), toStoredClaims(target, result, generatedAt));
    }

    private static List<StoredClaim> toStoredClaims(
            RecommendationTarget target, RecommendationResult result, Instant generatedAt) {

        return result.claims().stream()
                .map(claim -> new StoredClaim(
                        target.recordingId(),
                        target.eventType(),
                        target.hubId(),
                        target.workspaceId(),
                        target.projectId(),
                        target.projectName(),
                        claim.claim().title(),
                        claim.grounded() ? claim.frame().name() : claim.claim().citedFrame(),
                        claim.claim().sourcePath(),
                        claim.grounded(),
                        claim.sourceFound(),
                        claim.grounded() ? claim.frame().selfPct() : 0.0,
                        claim.grounded() ? claim.frame().totalPct() : 0.0,
                        generatedAt))
                .toList();
    }

    /**
     * The recommendation results already stored for a recording (one per event type), without generating
     * anything. Backs the GET endpoint that restores previously generated recommendations on page load.
     */
    public List<GeneratedRecommendation> peek(String recordingId) {
        return recommendationRepository.findByRecording(recordingId);
    }

    /**
     * The stored claims for a recording, so the UI can show which findings were confirmed against the
     * profile and which the model could not substantiate.
     */
    public List<StoredClaim> peekClaims(String recordingId) {
        return claimRepository.findByRecording(recordingId);
    }

    private FlamegraphAiPrompt resolvePrompt(String recordingId, String eventType, String projectId) {
        List<FlamegraphAiPrompt> prompts = promptManager.getPrompts(recordingId, projectId);
        return prompts.stream()
                .filter(p -> p.eventType().equals(eventType))
                .findFirst()
                .orElseThrow(() -> Exceptions.invalidRequest(
                        "No prompt available for event type: " + eventType));
    }

    private String resolveToken(VersionControlSystem vcs) {
        if (!vcs.hasCredentials()) {
            return null;
        }
        return Json.read(vcs.credentials(), VersionControlSystemCredentials.class).token();
    }
}
