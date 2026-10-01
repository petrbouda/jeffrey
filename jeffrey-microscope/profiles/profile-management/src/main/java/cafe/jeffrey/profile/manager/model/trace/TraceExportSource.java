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
 * Everything the trace bundle for a coding agent is written from.
 * <p>
 * The detail is the one the waterfall opens with — folded runs and all — so the bundle's tree has
 * the same shape. The promoted groups are there because the detail no longer carries every I/O
 * span, and the bundle's I/O accounting has to count each of them.
 *
 * @param detail   the trace as the waterfall receives it
 * @param context  what the JVM was doing to it
 * @param promoted the synthesized spans, totalled per event type and payload
 */
public record TraceExportSource(TraceDetail detail, TraceContext context, List<TracePromotedGroup> promoted) {

    public TraceExportSource {
        promoted = List.copyOf(promoted);
    }
}
