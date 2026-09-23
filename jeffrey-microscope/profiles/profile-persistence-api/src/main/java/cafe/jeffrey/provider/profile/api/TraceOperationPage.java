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

import java.util.List;

/**
 * One page of operations, with how many the filter matched in total.
 * <p>
 * The total is what turns a truncated list from a dead end into a page: without it the UI can say
 * "here are 100 operations" but not whether that is all of them, which is exactly the question a
 * reader looking at a capped list is asking. It counts what the filter matched, not what the table
 * holds, so it still means something once a search has been typed.
 *
 * @param operations    the rows for this page, already ordered
 * @param totalMatching how many distinct trace types the same filter matches, ignoring limit and
 *                      offset
 */
public record TraceOperationPage(List<TraceOperationRecord> operations, long totalMatching) {

    public static final TraceOperationPage EMPTY = new TraceOperationPage(List.of(), 0);

    public TraceOperationPage {
        operations = List.copyOf(operations);
    }
}
