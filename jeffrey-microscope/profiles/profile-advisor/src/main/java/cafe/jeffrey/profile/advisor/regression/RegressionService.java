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

package cafe.jeffrey.profile.advisor.regression;

import cafe.jeffrey.profile.advisor.prompt.AdvisorPrompt;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPromptManager;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPromptManagerFactory;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPromptType;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettings;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettingsResolver;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.ProfileInfo;

/**
 * Compares two profiles of the same application and reports which frames moved.
 *
 * <p>No model is involved. The comparison is arithmetic over two cached frame indexes, so it is exact,
 * instant and free — and because both indexes are the ones their prompts were built from, the numbers
 * here are the numbers the model saw for each side. That makes the output usable as a scoped follow-up
 * question ("only these frames moved; explain why") rather than a separate opinion.</p>
 */
public class RegressionService {

    private final AdvisorPromptManagerFactory promptManagerFactory;
    private final AdvisorSettingsResolver settingsResolver;

    public RegressionService(
            AdvisorPromptManagerFactory promptManagerFactory, AdvisorSettingsResolver settingsResolver) {

        this.promptManagerFactory = promptManagerFactory;
        this.settingsResolver = settingsResolver;
    }

    /**
     * Diffs {@code current} against {@code baseline} for one event type, using the current profile's
     * project threshold.
     *
     * @throws cafe.jeffrey.shared.common.exception.JeffreyClientException when either side has no prompt
     *                                                                     for the event type
     */
    public ProfileComparison compare(ProfileInfo baseline, ProfileInfo current, String eventType) {
        if (baseline.id().equals(current.id())) {
            throw Exceptions.invalidRequest("A profile cannot be compared against itself.");
        }

        AdvisorPromptType promptType = AdvisorPromptType.byEventCode(eventType)
                .orElseThrow(() -> Exceptions.invalidRequest(
                        "The Advisor does not analyze this event type: " + eventType));

        AdvisorSettings settings = settingsResolver.resolve(current);
        AdvisorPrompt baselinePrompt = promptOf(baseline, promptType, settings, "baseline");
        AdvisorPrompt currentPrompt = promptOf(current, promptType, settings, "current");

        return ProfileComparator.compare(
                eventType,
                baselinePrompt.frameIndex(),
                currentPrompt.frameIndex(),
                settings.regressionThresholdPp());
    }

    /**
     * A comparison rendered as a prompt fragment, so a follow-up analysis can be scoped to what actually
     * moved instead of re-reading the whole call tree.
     */
    public String promptFragment(ProfileComparison comparison) {
        return ProfileComparator.toPromptFragment(comparison);
    }

    /**
     * Resolves one side's prompt, generating it if the profile has never been analyzed. The side is
     * named in the error so a user with two profiles open knows which one is missing the data.
     */
    private AdvisorPrompt promptOf(
            ProfileInfo profile, AdvisorPromptType promptType, AdvisorSettings settings, String side) {

        AdvisorPromptManager promptManager = promptManagerFactory.apply(profile);
        try {
            return promptManager.resolve(promptType, settings.pruneThresholdPct());
        } catch (RuntimeException e) {
            throw Exceptions.invalidRequest(
                    "The " + side + " profile has no " + promptType.label() + " profile to compare.");
        }
    }
}
