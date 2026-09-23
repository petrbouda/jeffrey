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

/**
 * One slice of the recording, for the density strip above the search results: how many traces the
 * search matched, against how many there were.
 * <p>
 * Both numbers travel together because the strip means nothing without the second: a burst of
 * matches is only a burst if the profile was not equally busy everywhere.
 */
public record TraceAttributeTimelineBucket(
        long fromMillisFromBeginning,
        long matched,
        long total) {
}
