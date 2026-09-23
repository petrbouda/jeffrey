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
 * One page of operations, with how many the filter matched in total.
 * <p>
 * The total travels with the rows so the list can say whether what it drew is all there was. Without
 * it a capped list is indistinguishable from a complete one, which is the question a reader looking
 * at exactly 100 rows always has.
 *
 * @param operations    the page's rows, already ordered
 * @param totalMatching how many trace types match the same filter, ignoring the page bounds
 */
public record TraceOperationsPage(List<TraceOperationRow> operations, long totalMatching) {
}
