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

import pbouda.jeffrey.frameir.Frame;

import java.util.List;

public interface Traversable {

    /**
     * Evaluates the traversable object on the given frame. Generates the result of the evaluation,
     * whether the traversable should continue with the traversal or terminate it immediately
     * (it does not make sense to proceed).
     *
     * @param frame currently evaluated frame in the traversal.
     * @return the result of the evaluation hinting the next steps for traversing the other frames.
     */
    Next traverse(Frame frame);

    /**
     * Returns the list of frames that were matched and selected by the traversable object.
     *
     * @return the list of selected frames≥
     */
    List<Frame> selectedFrames();
}
