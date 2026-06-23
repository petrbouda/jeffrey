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
import cafe.jeffrey.performance.analyst.persistence.VersionControlSystem;
import cafe.jeffrey.performance.analyst.versioncontrolsystem.VersionControlSystemCredentials;
import cafe.jeffrey.performance.analyst.versioncontrolsystem.VersionControlSystemManager;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.ToolBinding;
import cafe.jeffrey.profile.ai.chat.ToolExchange;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Generates repository-aware AI recommendations for a recording. It resolves the project's configured
 * repository and the cached flamegraph prompt for the requested event type, clones the repository, then
 * runs a Claude analysis with read-only {@link RepoAnalysisTools} over the checkout — the same way an
 * agentic code assistant explores a codebase. The clone is always deleted afterwards.
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
    private final Clock clock;

    public RecordingRecommendationManager(
            VersionControlSystemManager versionControlSystemManager,
            RecordingAiPromptManager promptManager,
            RepositoryCloner repositoryCloner,
            AiChatBackend aiChatBackend,
            RepoToolsRegistry repoToolsRegistry,
            RepoToolsetFactory repoToolsetFactory,
            GeneratedRecommendationRepository recommendationRepository,
            Clock clock) {
        this.versionControlSystemManager = versionControlSystemManager;
        this.promptManager = promptManager;
        this.repositoryCloner = repositoryCloner;
        this.aiChatBackend = aiChatBackend;
        this.repoToolsRegistry = repoToolsRegistry;
        this.repoToolsetFactory = repoToolsetFactory;
        this.recommendationRepository = recommendationRepository;
        this.clock = clock;
    }

    /**
     * Produces the markdown recommendations for {@code recordingId}'s {@code eventType} prompt against
     * the repository configured for {@code projectId}. Reports the clone/analyze phases through
     * {@code sink}. Throws {@link cafe.jeffrey.shared.common.exception.JeffreyClientException} when no
     * repository is configured or the recording has no matching prompt.
     */
    public RecommendationResult generate(RecommendationTarget target, RecommendationProgressSink sink) {
        String projectId = target.projectId();
        String recordingId = target.recordingId();
        String eventType = target.eventType();

        VersionControlSystem vcs = versionControlSystemManager.find(projectId)
                .orElseThrow(() -> Exceptions.invalidRequest(
                        "No repository configured for this project. Configure it under Version Control System."));

        FlamegraphAiPrompt prompt = resolvePrompt(recordingId, eventType);
        String token = resolveToken(vcs);

        sink.cloning();
        try (ClonedRepository repository = repositoryCloner.clone(vcs.url(), token, vcs.platform())) {
            sink.analyzing();

            String raw = analyze(prompt, new RepoAnalysisTools(repository.root()));

            RecommendationResult result = RecommendationOutputParser.parse(raw);
            recommendationRepository.upsert(new GeneratedRecommendation(
                    recordingId, eventType, target.hubId(), target.workspaceId(), projectId, target.projectName(),
                    result.severity(), result.recommendations(), result.patch(), clock.instant()));

            LOG.info("Generated AI recommendations: projectId={} recordingId={} eventType={} severity={} recommendations_length={} has_patch={}",
                    projectId, recordingId, eventType, result.severity(), result.recommendations().length(), result.hasPatch());
            return result;
        }
    }

    /**
     * Runs the configured AI backend over the cloned checkout, exposing {@code tools} both in-process
     * (Spring AI providers) and over the run-scoped MCP endpoint (the Claude Code CLI). The {@code runId}
     * binds the tools in {@link RepoToolsRegistry} only for the duration of this call, since the clone is
     * deleted when the caller's try-with-resources closes.
     */
    private String analyze(FlamegraphAiPrompt prompt, RepoAnalysisTools tools) {
        String runId = UUID.randomUUID().toString();
        repoToolsRegistry.register(runId, tools);
        try {
            ToolBinding toolBinding = new ToolBinding(tools, repoToolsetFactory.forRun(runId));
            ToolExchange exchange = new ToolExchange(
                    RecommendationPrompts.SYSTEM_PROMPT,
                    null,
                    RecommendationPrompts.userMessage(prompt.label(), prompt.markdown()),
                    toolBinding,
                    SPAN_NAME);
            return aiChatBackend.analyze(exchange).text();
        } finally {
            repoToolsRegistry.unregister(runId);
        }
    }

    /**
     * The recommendation results already stored for a recording (one per event type), without generating
     * anything. Backs the GET endpoint that restores previously generated recommendations on page load.
     */
    public List<GeneratedRecommendation> peek(String recordingId) {
        return recommendationRepository.findByRecording(recordingId);
    }

    private FlamegraphAiPrompt resolvePrompt(String recordingId, String eventType) {
        List<FlamegraphAiPrompt> prompts = promptManager.getPrompts(recordingId);
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
