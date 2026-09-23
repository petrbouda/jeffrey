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

package cafe.jeffrey.profile.manager;

/**
 * How a trace or span id crosses the wire.
 * <p>
 * As a 16-char hex string, never as a JSON number: the ids are 64-bit, which exceeds JavaScript's
 * safe integer range, so a numeric type would silently round them in the browser. Hex is also how
 * every other tracer renders them, so an id copied out of Jeffrey is recognisable elsewhere.
 * <p>
 * One definition rather than one per manager: two views that spell the same id differently cannot
 * be linked to each other, and the bug does not show up until someone tries.
 */
public abstract class TraceIds {

    private TraceIds() {
    }

    public static String hex(long id) {
        return String.format("%016x", id);
    }
}
