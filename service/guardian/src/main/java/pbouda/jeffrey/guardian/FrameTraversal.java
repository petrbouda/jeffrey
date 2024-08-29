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

package pbouda.jeffrey.guardian;

import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.guardian.guard.Guard;
import pbouda.jeffrey.guardian.guard.Guard.Result;

import java.util.List;
import java.util.Map;

public class FrameTraversal {

    private final Frame frame;

    public FrameTraversal(Frame frame) {
        this.frame = frame;
    }

    public void traverseWith(List<Guard> guards) {
        _traverse(guards, frame);
    }

    private Result _traverse(List<Guard> guards, Frame frame) {
        Result current = Result.CONTINUE;
        for (Guard guard : guards) {
            Result result = guard.evaluate(frame);

            if (result == Result.TERMINATE_IMMEDIATELY) {
                return result;
            }

            current = updateResult(current, result);
        }

        // Go deep in the tree only if the current frame is market as CONTINUE with all guards.
        // SKIP_SUBTREE can cause the whole subtree to be skipped.
        if (current == Result.CONTINUE) {
            for (Map.Entry<String, Frame> entry : frame.entrySet()) {
                Result result = _traverse(guards, entry.getValue());

                // Fast path for termination to quickly go back in the current recursion
                if (result == Result.TERMINATE_IMMEDIATELY) {
                    return result;
                }
            }
        }

        return current;
    }

    private static Result updateResult(Result current, Result result) {
        if (current == Result.SKIP_SUBTREE) {
            return Result.SKIP_SUBTREE;
        } else {
            return result;
        }
    }
}
