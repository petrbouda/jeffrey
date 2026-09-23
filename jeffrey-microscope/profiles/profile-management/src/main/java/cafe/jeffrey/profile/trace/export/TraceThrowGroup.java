/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.profile.trace.export;

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
