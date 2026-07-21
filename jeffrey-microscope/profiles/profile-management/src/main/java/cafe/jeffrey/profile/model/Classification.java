/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.profile.model;

/**
 * Presentation-role flags a panel carries so the frontend can apply route-based show/hide without
 * inspecting the event code. The frontend hides {@code method} panels when method-tracing is suppressed,
 * and {@code nativeMemory}/{@code blocking} panels outside the primary flamegraph route.
 */
public record Classification(boolean method, boolean nativeMemory, boolean blocking) {

    public static final Classification PLAIN = new Classification(false, false, false);
}
