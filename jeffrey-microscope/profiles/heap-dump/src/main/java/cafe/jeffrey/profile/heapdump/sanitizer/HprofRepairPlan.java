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

package cafe.jeffrey.profile.heapdump.sanitizer;

import java.util.List;

/**
 * Output of {@link HprofRepairPlanner}: an ordered list of repair operations
 * paired with diagnostic metadata about what was found.
 *
 * @param repairs ordered repair operations (apply left-to-right)
 * @param result  diagnostic summary used by callers / logs
 */
public record HprofRepairPlan(List<HprofRepair> repairs, SanitizeResult result) {

    public HprofRepairPlan {
        repairs = List.copyOf(repairs);
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
    }

    public boolean isClean() {
        return repairs.isEmpty();
    }
}
