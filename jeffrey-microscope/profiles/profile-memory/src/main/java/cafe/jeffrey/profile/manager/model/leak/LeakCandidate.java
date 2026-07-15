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

package cafe.jeffrey.profile.manager.model.leak;

/**
 * One leak-candidate sample, from a {@code jdk.OldObjectSample} event — a live object that survived
 * long enough to be flagged as a potential leak.
 *
 * @param className              class of the leaked object
 * @param objectSizeBytes        shallow size of the object
 * @param objectAgeNanos         how long the object has been alive
 * @param arrayElements          element count when the object is an array (0 otherwise)
 * @param lastKnownHeapUsageBytes heap usage when the sample was taken
 */
public record LeakCandidate(
        String className,
        long objectSizeBytes,
        long objectAgeNanos,
        int arrayElements,
        long lastKnownHeapUsageBytes) {
}
