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

package pbouda.jeffrey.guardian.traverse;

import pbouda.jeffrey.frameir.Frame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FrameTraversal {

    private final Frame frame;

    public FrameTraversal(Frame frame) {
        this.frame = frame;
    }

    public void traverseWith(List<? extends Traversable> traversables) {
        _traverse(traversables, frame);
    }

    private Next _traverse(List<? extends Traversable> traversables, Frame frame) {
        List<Traversable> continues = new ArrayList<>();
        for (Traversable guard : traversables) {
            Next next = guard.traverse(frame);

            // Fast path for termination to quickly go back in the current recursion
            // Useful for the cases when the guard is able to terminate the whole traversal
            // e.g. Total Samples Guard - terminates the whole traversal if the total samples are not reached
            if (next == Next.TERMINATE_IMMEDIATELY) {
                return next;
            } else if (next == Next.CONTINUE) {
                continues.add(guard);
            }
        }

        if (!continues.isEmpty()) {
            for (Map.Entry<String, Frame> entry : frame.entrySet()) {
                Next next = _traverse(traversables, entry.getValue());

                // Fast path for termination to quickly go back in the current recursion
                if (next == Next.TERMINATE_IMMEDIATELY) {
                    return next;
                }
            }
            return Next.CONTINUE;
        } else {
            return Next.SKIP_SUBTREE;
        }
    }
}
