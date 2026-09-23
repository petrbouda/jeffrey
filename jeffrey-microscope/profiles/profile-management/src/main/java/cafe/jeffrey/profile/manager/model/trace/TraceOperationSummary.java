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

/**
 * The two things an operation's summary cannot work out from the trace list it already holds: what
 * its spans are, and which threads they ran on.
 * <p>
 * Everything else on that page — call count, percentiles, distribution, concurrency — is arithmetic
 * over the traces themselves, and is done where they are already loaded rather than fetched twice.
 *
 * @param spans   span names ranked by total time, inclusive of their children
 * @param threads how the spans divide between platform and virtual threads
 */
public record TraceOperationSummary(List<TraceOperationSpanRow> spans, TraceOperationThreads threads) {
}
