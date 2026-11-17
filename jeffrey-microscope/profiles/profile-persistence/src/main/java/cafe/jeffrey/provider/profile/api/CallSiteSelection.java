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

/** Which ranked rows a call-site read hands to Java, after calculating complete aggregates in SQL. */
public sealed interface CallSiteSelection {

    /** The busiest attributed sites, across all grouping values. */
    record Sites(int limit) implements CallSiteSelection {
        public Sites {
            requirePositive(limit);
        }
    }

    /** The busiest grouping values, each with its busiest sites and complete group figures. */
    record Groups(int limit, int sitesPerGroup) implements CallSiteSelection {
        public Groups {
            requirePositive(limit);
            requirePositive(sitesPerGroup);
        }
    }

    private static void requirePositive(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("A call-site selection limit must be positive");
        }
    }
}
