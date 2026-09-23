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
 * The stack behind one throw, <strong>topmost frame first</strong>.
 * <p>
 * Every frame is returned; which of them are worth showing is the reader's question, not this
 * layer's. The UI folds runs of library frames away and lets them be opened again, and a fold that
 * lived here could not be undone without another round trip.
 *
 * @param stacktraceId the id the throw carried, echoed back so a response can be matched to the
 *                     request that asked for it
 * @param frames       the frames, throwing frame first and {@code Thread.run} last. Empty when the
 *                     recording captured no stack, which is ordinary rather than an error
 */
public record TraceStacktrace(String stacktraceId, List<TraceStackFrameRow> frames) {
}
