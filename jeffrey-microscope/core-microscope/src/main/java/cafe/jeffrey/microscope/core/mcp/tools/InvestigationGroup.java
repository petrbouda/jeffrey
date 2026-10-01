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
package cafe.jeffrey.microscope.core.mcp.tools;

/**
 * The four groups the investigation areas are offered in — at most four areas each, so a host that
 * asks one question per group with up to four options can put the whole menu to the user at once.
 */
enum InvestigationGroup {

    /** Where the samples land in the code, and when. */
    CODE,

    /** What the application waited on: requests, the database, I/O, locks. */
    WAITING,

    /** The runtime underneath: collections, compilation, threads, native memory. */
    JVM,

    /** Whole-recording judgements and the setup the JVM ran with. */
    OVERALL
}
