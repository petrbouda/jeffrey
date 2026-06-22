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

package cafe.jeffrey.performance.analyst.recommendations;

import cafe.jeffrey.performance.analyst.persistence.SeverityCount;
import cafe.jeffrey.shared.common.model.Severity;

import java.util.List;

/**
 * The number of recordings at each severity (by their worst recommendation), for the Overview tiles.
 */
public record SeverityCounts(int critical, int high, int medium, int low) {

    public static SeverityCounts from(List<SeverityCount> counts) {
        int critical = 0;
        int high = 0;
        int medium = 0;
        int low = 0;
        for (SeverityCount count : counts) {
            switch (count.severity()) {
                case CRITICAL -> critical = count.count();
                case HIGH -> high = count.count();
                case MEDIUM -> medium = count.count();
                case LOW -> low = count.count();
            }
        }
        return new SeverityCounts(critical, high, medium, low);
    }

    public static SeverityCounts empty() {
        return new SeverityCounts(0, 0, 0, 0);
    }
}
