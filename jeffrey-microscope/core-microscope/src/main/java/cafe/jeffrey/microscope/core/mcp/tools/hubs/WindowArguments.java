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
package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a predefined window was given beside its name — from the tool call's arguments or from the
 * fields of the form the user filled in. Each is null when not given.
 *
 * @param minutes      the length for LAST_MINUTES, BEFORE and AROUND
 * @param atEpochMs    the moment BEFORE ends at and AROUND is centred on
 * @param startEpochMs CUSTOM's start
 * @param endEpochMs   CUSTOM's end
 */
public record WindowArguments(Integer minutes, Long atEpochMs, Long startEpochMs, Long endEpochMs) {

    public static final WindowArguments NONE = new WindowArguments(null, null, null, null);

    /** The arguments that were given. */
    public Set<WindowParam> present() {
        Set<WindowParam> present = EnumSet.noneOf(WindowParam.class);
        if (minutes != null) {
            present.add(WindowParam.MINUTES);
        }
        if (atEpochMs != null) {
            present.add(WindowParam.AT);
        }
        if (startEpochMs != null) {
            present.add(WindowParam.START);
        }
        if (endEpochMs != null) {
            present.add(WindowParam.END);
        }
        return present;
    }
}
