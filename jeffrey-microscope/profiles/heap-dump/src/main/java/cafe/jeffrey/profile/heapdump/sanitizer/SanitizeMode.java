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

/**
 * Selects how an HPROF sanitization is applied.
 */
public enum SanitizeMode {

    /**
     * Mutate the original heap dump file directly. Fast — no full file copy.
     * Destructive: the unrepaired bytes are lost.
     */
    IN_PLACE,

    /**
     * Duplicate the heap dump to a sibling {@code <name>.sanitized} file and
     * apply repairs there. Non-destructive: the original is preserved for
     * forensic purposes. Costs one full file copy.
     */
    COPY
}
