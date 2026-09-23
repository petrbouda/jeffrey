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

package cafe.jeffrey.profile.manager.model.trace;

import java.util.List;
import java.util.Map;

/**
 * What the JVM was doing to a trace, beside what the trace was doing itself.
 * <p>
 * This is the answer a waterfall cannot give on its own. A span that took 200 ms looks identical
 * whether it computed for 200 ms, waited on a lock, or was stopped by a collection — and those are
 * three different problems. Keeping the pauses, the per-span waits and the ranked summary in one
 * payload means the view draws one consistent story rather than three requests' worth of it.
 *
 * @param pauses          stop-the-world stretches overlapping the trace, in time order. Global, so
 *                        they are drawn across the whole waterfall rather than against any one span
 * @param throttleWindows CFS sampling windows in which the container was CPU-throttled. Global like
 *                        the pauses, but weaker: a window says throttling happened inside it, not
 *                        when, so it is drawn and totalled differently and never joins the summary
 * @param spanWaits       per-span waiting, keyed by hex span id, each list ordered longest first. A
 *                        span that only ever ran is absent rather than present with zeroes
 * @param summary         the trace's time ranked by where it went, with everything unaccounted for
 *                        reported as the code's own work
 */
public record TraceContext(
        List<TracePause> pauses,
        List<TraceThrottleWindow> throttleWindows,
        Map<String, List<TraceContextSlice>> spanWaits,
        List<TraceContextSlice> summary) {

    public static final TraceContext EMPTY =
            new TraceContext(List.of(), List.of(), Map.of(), List.of());
}
