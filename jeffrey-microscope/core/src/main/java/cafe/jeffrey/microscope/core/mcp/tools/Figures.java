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

import java.math.BigDecimal;

/**
 * The conversions a dashboard's figures go through on their way into an MCP record: a sentinel for
 * "not recorded" becomes {@code null}, never a number a reader would take for a measurement, and a
 * {@link BigDecimal} ratio becomes a plain number.
 */
final class Figures {

    /** What the event builders write for a size or port the event did not carry. */
    private static final long NOT_RECORDED = -1L;

    private Figures() {
    }

    /** A byte count, or {@code null} when the event did not carry one. */
    static Long bytes(long value) {
        return value <= NOT_RECORDED ? null : value;
    }

    /** A port, or {@code null} when the event did not carry one. */
    static Integer port(int value) {
        return value <= NOT_RECORDED ? null : value;
    }

    /** A name, or {@code null} when the event carried none - an empty string is not a host or a thread. */
    static String name(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** A ratio or percentage as a plain number; a missing one is 0, as the dashboards draw it. */
    static double number(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}
