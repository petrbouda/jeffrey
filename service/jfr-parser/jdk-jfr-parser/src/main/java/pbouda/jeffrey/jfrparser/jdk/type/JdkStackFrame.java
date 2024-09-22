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

package pbouda.jeffrey.jfrparser.jdk.type;

import jdk.jfr.consumer.RecordedFrame;
import pbouda.jeffrey.jfrparser.api.type.JfrMethod;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;

public record JdkStackFrame(RecordedFrame frame) implements JfrStackFrame {

    @Override
    public String type() {
        return frame.getType();
    }

    @Override
    public int lineNumber() {
        return frame.getLineNumber();
    }

    @Override
    public int bytecodeIndex() {
        return frame.getBytecodeIndex();
    }

    @Override
    public JfrMethod method() {
        return new JdkMethod(frame.getMethod());
    }
}
