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


package cafe.jeffrey.provider.profile.api;

/** A complete attributed group, including sites omitted by the per-group cap; text is cut only after grouping. */
public record CallSiteGroupRow(
        String key,
        String value,
        Integer valueChars,
        long count,
        long totalNanos,
        long sites) {

    public CallSiteGroupRow {
        if (count < 0 || totalNanos < 0 || sites < 0) {
            throw new IllegalArgumentException("Invalid call-site group figures");
        }
    }
}
