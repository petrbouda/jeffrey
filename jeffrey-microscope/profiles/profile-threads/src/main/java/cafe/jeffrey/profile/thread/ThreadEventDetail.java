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

package cafe.jeffrey.profile.thread;

import java.util.List;

/**
 * One event behind a timeline band, with the field values the tooltip renders. The values are
 * positional and line up with the fields declared for the matching state in
 * {@link ThreadMetadata} — the same contract the timeline has always used, only fetched per band
 * instead of shipped with every event.
 *
 * @param startOffset offset of the event from the beginning of the recording, in nanoseconds
 * @param width       length of the event in nanoseconds
 * @param values      field values, in the order the state's metadata declares them
 */
public record ThreadEventDetail(long startOffset, long width, List<Object> values) {
}
