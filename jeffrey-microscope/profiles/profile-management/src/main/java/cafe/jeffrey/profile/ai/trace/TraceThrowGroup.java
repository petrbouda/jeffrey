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

package cafe.jeffrey.profile.ai.trace;

import java.util.List;

/**
 * Every throw of one class carrying one message, collapsed into a single finding.
 * <p>
 * Grouped rather than listed because the interesting number is the count. Forty-three individual
 * throws read as forty-three problems; the same forty-three as "one class, forty-three times, none
 * of them escaped" reads as what it is — a loop paying for stack captures it never needed.
 *
 * @param thrownClass the class that was thrown
 * @param message     the message it carried, or {@code null} when it carried none
 * @param eventType   {@code jdk.JavaExceptionThrow} or {@code jdk.JavaErrorThrow}
 * @param count       how many times this throw happened inside the trace
 * @param escaped     how many of those are why their span failed; {@code 0} means every one was
 *                    caught, which is the distinction between a cost and a failure
 * @param spans       where they were thrown, ranked by how many each accounted for
 */
record TraceThrowGroup(
        String thrownClass,
        String message,
        String eventType,
        long count,
        long escaped,
        List<TraceThrowGroup.Site> spans) {

    /** One span that threw this, and how often. */
    record Site(String spanName, long count) {
    }

    boolean hasEscaped() {
        return escaped > 0;
    }
}
