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

package pbouda.jeffrey.guardian.guard;

import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.guardian.GuardianResult;

public interface Guard {

    enum Result {
        /**
         * Guards has been processed and decides that there is no reason to stop processing.
         */
        CONTINUE,
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

    /**
     * Evaluates the guard on the given frame. Generates the result of the evaluation, whether the guard should continue
     * with the traversal or terminate it immediately (it does not make sense to proceed).
     *
     * @param frame currently evaluated frame in the traversal.
     * @return the result of the guard evaluation hinting the next steps for traversing the other frames.
     */
    Result evaluate(Frame frame);

    /**
     * The result of the guard evaluation with description and other information to correctly react on the result.
     * Moreover, the result contains the frame which was evaluated and caused the result.
     *
     * @return the result of the guard evaluation
     */
    GuardianResult result();
}
