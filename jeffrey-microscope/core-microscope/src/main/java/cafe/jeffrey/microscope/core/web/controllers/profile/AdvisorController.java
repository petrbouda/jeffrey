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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPrompt;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPromptManagerFactory;
import cafe.jeffrey.profile.advisor.run.AdvisorRunResult;
import cafe.jeffrey.profile.advisor.run.AdvisorRunner;
import cafe.jeffrey.profile.advisor.run.AdvisorStages;
import cafe.jeffrey.profile.advisor.run.AdvisorTarget;
import cafe.jeffrey.profile.advisor.run.BatchAdvisorProgress;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettings;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettingsResolver;
import cafe.jeffrey.profile.common.pipeline.PipelineRunResult;
import cafe.jeffrey.provider.profile.api.AdvisorClaimRow;
import cafe.jeffrey.provider.profile.api.AdvisorRecommendationRow;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.api.PipelineRunRepository;
import cafe.jeffrey.provider.profile.api.ProfileAdvisorRepository;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.util.List;

/**
 * The profile Advisor: launch a batch run over every event type, poll its per-type progress, and read
 * back what was stored.
 *
 * <p>A run is asynchronous and returns 202 rather than holding the request open, because it takes
 * minutes. Progress is polled, following the heap-dump initialization pattern; the response carries no
 * result payload, since a completed type's artifacts are in the profile database and are read from there
 * by the same endpoint a fresh page load uses.</p>
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/advisor")
public class AdvisorController {

    /**
     * A cached prompt as the browser sees it. The frame index is deliberately omitted: it is large, and
     * the UI has no use for it — grounding happens server-side against the same copy.
     */
    public record PromptResponse(String eventType, String label, long samples, String markdown) {

        static PromptResponse from(AdvisorPrompt prompt) {
            return new PromptResponse(
                    prompt.eventType(), prompt.label(), prompt.samples(), prompt.markdown());
        }
    }

    /**
     * A profile group the Advisor can analyze for this profile, so the UI offers only tabs that exist.
     */
    public record EventTypeResponse(String eventType, String label) {
    }

    public record ClaimResponse(
            String eventType,
            String title,
            String citedFrame,
            String sourcePath,
            boolean grounded,
            boolean sourceFound,
            double selfPct,
            double totalPct) {

        static ClaimResponse from(AdvisorClaimRow claim) {
            return new ClaimResponse(
                    claim.eventType(), claim.title(), claim.citedFrame(), claim.sourcePath(),
                    claim.grounded(), claim.sourceFound(), claim.selfPct(), claim.totalPct());
        }
    }

    /**
     * A stored recommendation with the claims behind it.
     */
    public record RecommendationResponse(
            String eventType,
            String severity,
            String recommendations,
            String sourceRef,
            long inputTokens,
            long outputTokens,
            Double costUsd,
            long generatedAt,
            List<ClaimResponse> claims) {
    }

    /**
     * Which event types to analyze. An empty or absent list means "every type this profile has" — the
     * common case, since the run processes them all.
     */
    public record RunRequest(List<String> eventTypes) {

        public RunRequest {
            eventTypes = eventTypes == null ? List.of() : List.copyOf(eventTypes);
        }
    }

    private final ProfileManagerResolver resolver;
    private final AdvisorRunner advisorRunner;
    private final AdvisorPromptManagerFactory promptManagerFactory;
    private final AdvisorSettingsResolver settingsResolver;
    private final DatabaseManagerResolver databaseManagerResolver;
    private final ProfilePersistenceProvider persistenceProvider;

    public AdvisorController(
            ProfileManagerResolver resolver,
            AdvisorRunner advisorRunner,
            AdvisorPromptManagerFactory promptManagerFactory,
            AdvisorSettingsResolver settingsResolver,
            DatabaseManagerResolver databaseManagerResolver,
            ProfilePersistenceProvider persistenceProvider) {

        this.resolver = resolver;
        this.advisorRunner = advisorRunner;
        this.promptManagerFactory = promptManagerFactory;
        this.settingsResolver = settingsResolver;
        this.databaseManagerResolver = databaseManagerResolver;
        this.persistenceProvider = persistenceProvider;
    }

    @GetMapping("/event-types")
    public List<EventTypeResponse> eventTypes(@PathVariable("profileId") String profileId) {
        ProfileInfo profile = resolver.resolve(profileId).info();
        return promptManagerFactory.apply(profile).availableTypes().stream()
                .map(type -> new EventTypeResponse(type.primaryEventType().code(), type.label()))
                .toList();
    }

    @GetMapping("/prompts")
    public List<PromptResponse> prompts(@PathVariable("profileId") String profileId) {
        ProfileInfo profile = resolver.resolve(profileId).info();
        return promptManagerFactory.apply(profile).peek().stream()
                .map(PromptResponse::from)
                .toList();
    }

    /**
     * Rebuilds every prompt the profile can produce. This is how a changed prune threshold takes effect,
     * since the setting only reaches the model through the markdown.
     */
    @PostMapping("/prompts")
    public List<PromptResponse> regeneratePrompts(@PathVariable("profileId") String profileId) {
        ProfileInfo profile = resolver.resolve(profileId).info();
        AdvisorSettings settings = settingsResolver.resolve(profile);

        return promptManagerFactory.apply(profile).generateAll(settings.pruneThresholdPct()).stream()
                .map(PromptResponse::from)
                .toList();
    }

    @GetMapping("/recommendations")
    public List<RecommendationResponse> recommendations(@PathVariable("profileId") String profileId) {
        ProfileInfo profile = resolver.resolve(profileId).info();
        ProfileAdvisorRepository advisorRepository = persistenceProvider.repositories()
                .newAdvisorRepository(databaseManagerResolver.open(profile));

        List<AdvisorClaimRow> claims = advisorRepository.findClaims();
        return advisorRepository.findRecommendations().stream()
                .map(row -> toResponse(row, claims))
                .toList();
    }

    /**
     * Launches a batch that analyzes every requested event type (or every available one when none are
     * named). Returns 202 with the batch snapshot either way: the caller's next move is the same — poll
     * the run — whether this request started it or found one already going.
     */
    @PostMapping("/run")
    public ResponseEntity<BatchAdvisorProgress> run(
            @PathVariable("profileId") String profileId,
            @RequestBody(required = false) RunRequest request) {

        ProfileInfo profile = resolver.resolve(profileId).info();
        List<AdvisorTarget> targets = resolveTargets(profile, request);
        if (targets.isEmpty()) {
            throw Exceptions.invalidRequest("This profile has no event types the Advisor can analyze.");
        }

        advisorRunner.startBatch(profile, targets);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(advisorRunner.batchProgress(profileId));
    }

    @GetMapping("/run/progress")
    public BatchAdvisorProgress runProgress(@PathVariable("profileId") String profileId) {
        return advisorRunner.batchProgress(profileId);
    }

    /**
     * The last batch run's stored timeline (per-type, per-step durations), or null when the Advisor has
     * never run for this profile. This is what the Overview page re-renders as the kept, static timeline.
     */
    @GetMapping("/run/result")
    public AdvisorRunResult runResult(@PathVariable("profileId") String profileId) {
        List<PipelineRunResult> runs = pipelineRunRepository(profileId).findAll(AdvisorStages.PIPELINE_ID);
        return runs.isEmpty() ? null : AdvisorRunResult.from(runs);
    }

    @GetMapping("/run/result/exists")
    public boolean runResultExists(@PathVariable("profileId") String profileId) {
        return !pipelineRunRepository(profileId).findAll(AdvisorStages.PIPELINE_ID).isEmpty();
    }

    /**
     * Throws away everything the Advisor has derived for this profile — recommendations, claims, the
     * cached prompts and the kept run timeline — so the next run starts from nothing. The counterpart
     * of the heap dump's cache deletion, and a POST for the same reason: it is an action, not the
     * removal of the resource the path names.
     *
     * <p>Refused while a run is in flight. Deleting mid-run would only look like it worked: the batch
     * would write its rows back moments later, leaving a half-cleared profile.</p>
     */
    @PostMapping("/delete-results")
    public void deleteResults(@PathVariable("profileId") String profileId) {
        if (advisorRunner.batchProgress(profileId).isRunning()) {
            throw Exceptions.invalidRequest(
                    "An advisor run is in progress for this profile. Cancel it before clearing results.");
        }

        advisorRepository(profileId).deleteAll();
        pipelineRunRepository(profileId).deleteAll(AdvisorStages.PIPELINE_ID);
        advisorRunner.forget(profileId);
    }

    private ProfileAdvisorRepository advisorRepository(String profileId) {
        ProfileInfo profile = resolver.resolve(profileId).info();
        return persistenceProvider.repositories().newAdvisorRepository(databaseManagerResolver.open(profile));
    }

    private PipelineRunRepository pipelineRunRepository(String profileId) {
        ProfileInfo profile = resolver.resolve(profileId).info();
        return persistenceProvider.repositories()
                .newPipelineRunRepository(databaseManagerResolver.open(profile));
    }

    /**
     * The event types to run: the ones the profile can actually produce, narrowed to any explicitly
     * requested. A requested code the profile does not have is dropped rather than rejected, so a stale
     * selection cannot fail the whole run.
     */
    private List<AdvisorTarget> resolveTargets(ProfileInfo profile, RunRequest request) {
        List<String> available = promptManagerFactory.apply(profile).availableTypes().stream()
                .map(type -> type.primaryEventType().code())
                .toList();

        List<String> requested = request == null ? List.of() : request.eventTypes();
        List<String> selected = requested.isEmpty()
                ? available
                : requested.stream().filter(available::contains).toList();

        return selected.stream()
                .map(code -> AdvisorTarget.of(profile, code))
                .toList();
    }


    @DeleteMapping("/run")
    public ResponseEntity<Void> cancel(@PathVariable("profileId") String profileId) {
        return advisorRunner.cancel(profileId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    private static RecommendationResponse toResponse(
            AdvisorRecommendationRow row, List<AdvisorClaimRow> allClaims) {

        List<ClaimResponse> claims = allClaims.stream()
                .filter(claim -> claim.eventType().equals(row.eventType()))
                .map(ClaimResponse::from)
                .toList();

        return new RecommendationResponse(
                row.eventType(),
                row.severity(),
                row.recommendations(),
                row.sourceRef(),
                row.inputTokens(),
                row.outputTokens(),
                row.costUsd(),
                row.generatedAt().toEpochMilli(),
                claims);
    }
}
