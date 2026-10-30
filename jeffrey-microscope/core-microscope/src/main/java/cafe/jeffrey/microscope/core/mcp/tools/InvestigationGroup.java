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
 * The groups the investigation areas are offered in — at most four areas each, and at most four groups
 * per {@link MenuSubject}, so a host that asks one question per group with up to four options can put a
 * profile's whole menu to the user at once.
 */
enum InvestigationGroup {

    /** Where the samples land in the code, and when. */
    CODE(MenuSubject.RECORDING),

    /** What the application waited on: requests and calls, the database, I/O, locks. */
    WAITING(MenuSubject.RECORDING),

    /** The runtime underneath: collections, compilation, threads, native memory. */
    JVM(MenuSubject.RECORDING),

    /** Whole-recording judgements, what was thrown, and the setup the JVM ran with. */
    OVERALL(MenuSubject.RECORDING),

    /** What fills the heap: its totals, the classes and packages, the biggest objects. */
    HEAP_CONTENTS(MenuSubject.HEAP_DUMP),

    /** What keeps it alive: leak suspects, the dominator tree, class loaders, GC roots. */
    HEAP_RETENTION(MenuSubject.HEAP_DUMP),

    /** Memory spent on nothing: duplicated strings and empty or oversized collections. */
    HEAP_WASTE(MenuSubject.HEAP_DUMP);

    private final MenuSubject subject;

    InvestigationGroup(MenuSubject subject) {
        this.subject = subject;
    }

    MenuSubject subject() {
        return subject;
    }
}
