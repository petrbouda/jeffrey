/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.profile.guardian.guard;

import pbouda.jeffrey.profile.common.analysis.AnalysisResult.Severity;

import java.math.BigDecimal;

public record Matched(Severity severity, String color, BigDecimal percent) {
    public static Matched severity(Severity severity, BigDecimal percent) {
        return new Matched(severity, severity.color(), percent);
    }

    public static Matched ok(BigDecimal percent) {
        return new Matched(Severity.OK, Severity.OK.color(), percent);
    }

    public static Matched warning(BigDecimal percent) {
        return new Matched(Severity.WARNING, Severity.WARNING.color(), percent);
    }
}
