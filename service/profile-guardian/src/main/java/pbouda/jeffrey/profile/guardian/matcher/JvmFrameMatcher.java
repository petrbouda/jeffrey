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

package pbouda.jeffrey.profile.guardian.matcher;

import pbouda.jeffrey.common.model.FrameType;
import pbouda.jeffrey.frameir.Frame;

public class JvmFrameMatcher implements FrameMatcher {

    private final String parentName;
    private final String frameName;

    public JvmFrameMatcher(String frameName) {
        this(null, frameName);
    }

    public JvmFrameMatcher(String parentName, String frameName) {
        this.parentName = parentName;
        this.frameName = frameName;
    }

    @Override
    public boolean matches(Frame frame) {
        return frame.frameType() == FrameType.CPP
                && frame.methodName().equals(frameName)
                && (parentName == null || frame.parent().methodName().equals(parentName));
    }
}
