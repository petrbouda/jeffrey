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

package cafe.jeffrey.provider.profile.api;

import java.util.List;
import java.util.Optional;

/**
 * Advisor artifacts stored in a single profile's database: the cached prompts, the recommendation
 * results, and the checked claims behind them. Everything here is keyed by event type alone, because
 * the database itself already scopes the data to one profile.
 */
public interface ProfileAdvisorRepository {

    List<AdvisorPromptRow> findPrompts();

    Optional<AdvisorPromptRow> findPrompt(String eventType);

    /**
     * Stores a generated prompt, replacing any previous one for the same event type. Re-generating is
     * how a changed prune threshold takes effect, so this must overwrite rather than fail.
     */
    void upsertPrompt(AdvisorPromptRow prompt);

    List<AdvisorRecommendationRow> findRecommendations();

    void upsertRecommendation(AdvisorRecommendationRow recommendation);

    List<AdvisorClaimRow> findClaims();

    /**
     * Replaces the claims of one event type with {@code claims}. A re-run supersedes its predecessor
     * entirely: keeping the old rows alongside the new would present two contradictory verdicts about
     * the same profile as if both were current.
     */
    void replaceClaims(String eventType, List<AdvisorClaimRow> claims);

    /** The last batch run's stored timeline, or empty when the Advisor has never run for this profile. */
    Optional<AdvisorRunResultRow> findRunResult();

    boolean runResultExists();

    /** Stores the last run's timeline, replacing any previous one (a new run supersedes it). */
    void storeRunResult(AdvisorRunResultRow runResult);
}
