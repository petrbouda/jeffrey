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

package pbouda.jeffrey.guardian.matcher;

import pbouda.jeffrey.frameir.Frame;

public class SuffixFrameMatcher implements FrameMatcher {

    private final String suffix;

    public SuffixFrameMatcher(String suffix) {
        this.suffix = suffix;
    }

    @Override
    public boolean matches(Frame frame) {
        return frame.methodName().endsWith(suffix);
    }
}