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

package cafe.jeffrey.profile.advisor.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.flamegraph.ai.AiExportConfig;
import cafe.jeffrey.flamegraph.api.DbBasedFlamegraphGenerator;
import cafe.jeffrey.profile.common.config.GraphComponents;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.provider.profile.api.AdvisorPromptRow;
import cafe.jeffrey.provider.profile.api.ProfileAdvisorRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;
import cafe.jeffrey.shared.common.GraphType;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.ProfilingStartEnd;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds — and caches — the flamegraph prompt an advisor run reads, one per profile group present in
 * the profile.
 *
 * <p>Standalone Performance Analyst re-parsed the JFR file for this, because it has no profile
 * database. Microscope has one, so the call tree comes from the same provider the Flamegraph page uses
 * and the markdown is byte-for-byte what "Copy for AI" produces. The prompt is therefore a projection
 * of data that already exists, not a second parse of the recording that could disagree with the first.</p>
 *
 * <p>What is cached is the <em>complete user message</em>, not the markdown alone. Composing it here
 * rather than at run time makes the prompt a real artifact: the run sends the stored string verbatim,
 * and the Advisor's Prompt page shows exactly what was asked rather than a reconstruction. Alongside it
 * the profile's dominant self share is stored, because severity is graded from that number and it
 * should be the one measured when the prompt was built.</p>
 */
public class AdvisorPromptManager {

    private static final Logger LOG = LoggerFactory.getLogger(AdvisorPromptManager.class);

    private final ProfileEventTypeRepository eventTypeRepository;
    private final ProfileEventStreamRepository eventStreamRepository;
    private final ProfileAdvisorRepository advisorRepository;
    private final ProfilingStartEnd profilingStartEnd;
    private final Clock clock;

    public AdvisorPromptManager(
            ProfileEventTypeRepository eventTypeRepository,
            ProfileEventStreamRepository eventStreamRepository,
            ProfileAdvisorRepository advisorRepository,
            ProfilingStartEnd profilingStartEnd,
            Clock clock) {

        this.eventTypeRepository = eventTypeRepository;
        this.eventStreamRepository = eventStreamRepository;
        this.advisorRepository = advisorRepository;
        this.profilingStartEnd = profilingStartEnd;
        this.clock = clock;
    }

    /**
     * The prompts already cached for this profile, without generating anything. Backs the page load, so
     * opening the Advisor never triggers a call tree walk the user did not ask for.
     */
    public List<AdvisorPrompt> peek() {
        return advisorRepository.findPrompts().stream()
                .map(AdvisorPromptManager::toPrompt)
                .toList();
    }

    /**
     * The prompt for {@code promptType}, generating and caching it when absent.
     */
    public AdvisorPrompt resolve(AdvisorPromptType promptType, double pruneThresholdPct) {
        return advisorRepository.findPrompt(promptType.primaryEventType().code())
                .map(AdvisorPromptManager::toPrompt)
                .orElseGet(() -> generate(promptType, pruneThresholdPct)
                        .orElseThrow(() -> Exceptions.invalidRequest(
                                "This profile has no " + promptType.label() + " samples to analyze.")));
    }

    /**
     * Regenerates every prompt the profile can produce, replacing whatever was cached. This is how a
     * changed prune threshold takes effect — the setting only reaches the model through the markdown.
     */
    public List<AdvisorPrompt> generateAll(double pruneThresholdPct) {
        List<AdvisorPrompt> prompts = new ArrayList<>();
        for (AdvisorPromptType promptType : AdvisorPromptType.values()) {
            generate(promptType, pruneThresholdPct).ifPresent(prompts::add);
        }
        return prompts;
    }

    /**
     * The prompt types this profile can actually answer, so the UI offers only the tabs that exist.
     */
    public List<AdvisorPromptType> availableTypes() {
        return List.of(AdvisorPromptType.values()).stream()
                .filter(promptType -> resolveSampledType(promptType).isPresent())
                .toList();
    }

    private Optional<AdvisorPrompt> generate(AdvisorPromptType promptType, double pruneThresholdPct) {
        Optional<Type> sampledType = resolveSampledType(promptType);
        if (sampledType.isEmpty()) {
            return Optional.empty();
        }

        Type eventType = sampledType.get();
        DbBasedFlamegraphGenerator generator = new DbBasedFlamegraphGenerator(
                eventStreamRepository, pruneThresholdPct, new AiExportConfig(pruneThresholdPct));

        DbBasedFlamegraphGenerator.AiExport export = generator.generateAiExportWithFrames(parameters(eventType));

        AdvisorPrompt prompt = new AdvisorPrompt(
                promptType.primaryEventType().code(),
                promptType.label(),
                export.root().totalSamples(),
                AdvisorPrompts.userMessage(promptType.label(), export.markdown()),
                DominantSelfShare.of(export.root()),
                clock.instant());

        advisorRepository.upsertPrompt(new AdvisorPromptRow(
                prompt.eventType(),
                prompt.label(),
                prompt.samples(),
                prompt.prompt(),
                prompt.dominantSelfPct(),
                prompt.generatedAt()));

        LOG.info("Generated advisor prompt: prompt_type={} event_type={} total_samples={} dominant_self_pct={}",
                promptType.name(), eventType.code(), prompt.samples(), prompt.dominantSelfPct());
        return Optional.of(prompt);
    }

    /**
     * Picks the first event type of the group that actually produced samples. A recording carries either
     * {@code ObjectAllocationSample} or the older TLAB pair depending on how it was recorded, and the
     * person reading the result does not care which.
     */
    private Optional<Type> resolveSampledType(AdvisorPromptType promptType) {
        for (Type type : promptType.eventTypes()) {
            boolean sampled = eventTypeRepository.eventSummaries(type)
                    .map(EventSummary::samples)
                    .orElse(0L) > 0;
            if (sampled) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * Whole-profile parameters: no thread scoping, no search, no markers. The advisor asks "where does
     * this service spend its time", which is the unfiltered question; every filter the Flamegraph page
     * offers would narrow it into a different one.
     */
    private GraphParameters parameters(Type eventType) {
        return GraphParameters.builder()
                .withEventType(eventType)
                .withTimeRange(new RelativeTimeRange(profilingStartEnd))
                .withThreads(List.of())
                .withThreadMode(false)
                .withUseWeight(eventType.isAllocationEvent() || eventType.isBlockingEvent())
                .withParseLocation(true)
                .withGraphType(GraphType.PRIMARY)
                .withGraphComponents(GraphComponents.FLAMEGRAPH_ONLY)
                .build();
    }

    /** Rebuilds the domain prompt from its stored row. */
    private static AdvisorPrompt toPrompt(AdvisorPromptRow row) {
        return new AdvisorPrompt(
                row.eventType(),
                row.label(),
                row.samples(),
                row.prompt(),
                row.dominantSelfPct(),
                row.generatedAt());
    }
}
