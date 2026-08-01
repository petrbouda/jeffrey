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

import java.util.List;

/**
 * One event type's outcome in a stored run result: its terminal status, total measured time, and the
 * per-step breakdown. The phase card in the run timeline is rendered from this.
 *
 * @param eventType the sample event type analyzed
 * @param status    {@code completed} or {@code failed}
 * @param totalMs   the sum of the step durations
 * @param steps     the four timed steps
 */
public record AdvisorTypeResult(String eventType, String status, long totalMs, List<AdvisorStepResult> steps) {

    public AdvisorTypeResult {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
