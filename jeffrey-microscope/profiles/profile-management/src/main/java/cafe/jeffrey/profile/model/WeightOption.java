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
 * The "Use weight" flamegraph-card setting. {@code applicable} whether the toggle is offered,
 * {@code defaultOn} its initial state, {@code label} the toggle text (e.g. "CPU Time", "Total
 * Allocation"; null when not applicable), and {@code kind} how the weight value is formatted. The kind
 * is always set — the frontend also uses it to format the sample-interval detail row even when the
 * weight toggle itself is hidden.
 */
public record WeightOption(boolean applicable, boolean defaultOn, String label, WeightKind kind) {

    public WeightOption {
        if (kind == null) {
            throw new IllegalArgumentException("WeightOption.kind must not be null");
        }
    }
}
