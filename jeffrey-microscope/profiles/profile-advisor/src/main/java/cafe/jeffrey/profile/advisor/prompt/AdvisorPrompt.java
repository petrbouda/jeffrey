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

import java.time.Instant;

/**
 * One generated AI prompt for a profile, per sample event type.
 *
 * <p>{@code prompt} is the complete user message as the model receives it, composed once here rather
 * than assembled at run time. That makes it a real artifact: the run sends this string verbatim, and
 * the Advisor's Prompt page can show exactly what was asked without reconstructing it from parts that
 * may since have changed.</p>
 *
 * @param eventType       the sample event type this prompt describes
 * @param label           the tab label, e.g. "CPU"
 * @param samples         the profile's total sample count, for the chip beside the label
 * @param prompt          the full user message sent to the model
 * @param dominantSelfPct the heaviest method's self share of the profile, which severity is graded from
 * @param generatedAt     when this prompt was built from the profile
 */
public record AdvisorPrompt(
        String eventType,
        String label,
        long samples,
        String prompt,
        double dominantSelfPct,
        Instant generatedAt) {
}
