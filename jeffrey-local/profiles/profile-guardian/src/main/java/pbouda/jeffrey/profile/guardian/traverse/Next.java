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

package pbouda.jeffrey.profile.guardian.traverse;

public enum Next {
    /**
     * Guards has not been started yet.
     */
    NOT_STARTED,
    /**
     * Guards has been processed and decides that there is no reason to stop processing.
     */
    CONTINUE,
    /**
     * Processing of the given guard is done and there is no reason to continue with the traversal for
     * this given guard.
     */
    DONE,
    /**
     * Immediately terminates the traversal of other the frames
     * (e.g. the total number of samples is too low start processing).
     */
    TERMINATE_IMMEDIATELY,
    /**
     * Skips the traversal of the current subtree
     * (e.g. the number of samples is too low to continue).
     */
    SKIP_SUBTREE
}
