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

/** Full filtered-read figures, calculated before any ranking cap; sites and groups count attributed rows. */
public record CallSiteTotals(
        long totalEvents,
        long totalNanos,
        long unattributedEvents,
        long sites,
        long groups) {

    public static final CallSiteTotals EMPTY = new CallSiteTotals(0, 0, 0, 0, 0);

    public CallSiteTotals {
        if (totalEvents < 0 || totalNanos < 0 || unattributedEvents < 0 || sites < 0 || groups < 0
                || unattributedEvents > totalEvents || groups > sites) {
            throw new IllegalArgumentException("Invalid call-site totals");
        }
    }
}
