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
import cafe.jeffrey.profile.advisor.prompt.AdvisorPromptType;
import cafe.jeffrey.profile.advisor.run.AdvisorProgress;
import cafe.jeffrey.profile.advisor.run.AdvisorRunner;
import cafe.jeffrey.profile.advisor.run.AdvisorStatus;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettings;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettingsResolver;
import cafe.jeffrey.provider.profile.api.AdvisorClaimRow;
import cafe.jeffrey.provider.profile.api.AdvisorRecommendationRow;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.api.ProfileAdvisorRepository;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.util.List;

/**
 * The profile Advisor: generate AI recommendations for a profile, poll the run, and read back what was
 * stored.
 *
 * <p>Generation is asynchronous and returns 202 rather than holding the request open, because a run
 * takes minutes. Progress is polled, following the heap-dump initialization pattern; the response
 * carries no result payload, since a completed run's artifacts are in the profile database and are read
 * from there by the same endpoint a fresh page load uses.</p>
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
     * A stored recommendation with the claims behind it. {@code verification} stays a raw JSON string:
     * the ladder is produced and consumed as a whole, and re-modelling it here would only add a mapping
     * that must be kept in step with the verifier for no gain.
     */
    public record RecommendationResponse(
            String eventType,
            String severity,
            String recommendations,
            String patch,
            String verification,
            String sourceRef,
            long inputTokens,
            long outputTokens,
            Double costUsd,
            long generatedAt,
            List<ClaimResponse> claims) {
    }

    public record GenerateRequest(String eventType) {
    }

    /**
     * The outcome of asking for a run. {@code started} is false when one was already in flight, which is
     * not an error — the caller simply watches the run that exists.
     */
    public record GenerateResponse(boolean started, AdvisorProgress progress) {
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

    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generate(
            @PathVariable("profileId") String profileId,
            @RequestBody GenerateRequest request) {

        if (request == null || request.eventType() == null || request.eventType().isBlank()) {
            throw Exceptions.invalidRequest("An event type is required to generate recommendations.");
        }
        if (AdvisorPromptType.byEventCode(request.eventType()).isEmpty()) {
            throw Exceptions.invalidRequest(
                    "The Advisor does not analyze this event type: " + request.eventType());
        }

        ProfileInfo profile = resolver.resolve(profileId).info();
        boolean started = advisorRunner.start(profile, request.eventType());

        // 202 either way: the caller's next move is the same — poll the run — whether this request
        // started it or found one already going.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new GenerateResponse(started, advisorRunner.progress(profileId)));
    }

    @GetMapping("/progress")
    public AdvisorProgress progress(@PathVariable("profileId") String profileId) {
        return advisorRunner.progress(profileId);
    }

    /**
     * The stage sequence the UI renders as a timeline. Served from the backend so the two cannot drift:
     * a stage added to the pipeline appears in the timeline without a frontend change.
     */
    @GetMapping("/stages")
    public List<String> stages() {
        return AdvisorStatus.ORDER.stream().map(Enum::name).toList();
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
                row.patch(),
                row.verificationJson(),
                row.sourceRef(),
                row.inputTokens(),
                row.outputTokens(),
                row.costUsd(),
                row.generatedAt().toEpochMilli(),
                claims);
    }
}
