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

public class NameBasedSingleTraverser implements Traversable {

    private final List<String> methodNames;
    private Next globalNext = Next.CONTINUE;
    private Frame selectedFrame;

    public NameBasedSingleTraverser(String methodName) {
        this(List.of(methodName));
    }

    public NameBasedSingleTraverser(List<String> methodNames) {
        this.methodNames = methodNames;
    }

    @Override
    public Next traverse(Frame frame) {
        if (globalNext != Next.DONE) {
            Frame current = frame;
            for (String methodName : methodNames) {
                current = current.get(methodName);
                if (current == null) {
                    this.globalNext = Next.DONE;
                    return this.globalNext;
                }
            }
            this.selectedFrame = current;
            this.globalNext = Next.DONE;
        }

        return globalNext;
    }

    @Override
    public List<Frame> selectedFrames() {
        if (selectedFrame == null) {
            return List.of();
        } else {
            return List.of(selectedFrame);
        }
    }
}
