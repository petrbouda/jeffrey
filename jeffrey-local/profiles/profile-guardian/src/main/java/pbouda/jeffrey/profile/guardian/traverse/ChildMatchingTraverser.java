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
import pbouda.jeffrey.profile.guardian.matcher.FrameMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChildMatchingTraverser implements Traversable {

    private final FrameMatcher frameMatcher;

    private Next globalNext = Next.CONTINUE;
    private List<Frame> selectedFrames;

    public ChildMatchingTraverser(FrameMatcher frameMatcher) {
        this.frameMatcher = frameMatcher;
    }

    @Override
    public Next traverse(Frame frame) {
        if (globalNext != Next.DONE) {
            List<Frame> result = new ArrayList<>();
            for (Map.Entry<String, Frame> entries : frame.entrySet()) {
                if (frameMatcher.matches(entries.getValue())) {
                    result.add(entries.getValue());
                }
            }

            this.selectedFrames = List.copyOf(result);
            this.globalNext = Next.DONE;
        }
        return globalNext;
    }

    @Override
    public List<Frame> selectedFrames() {
        return selectedFrames;
    }
}
