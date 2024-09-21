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

package pbouda.jeffrey.jfrparser.jafar.type;

import io.jafar.parser.api.types.JFRStackFrame;
import io.jafar.parser.api.types.JFRStackTrace;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;

import java.util.ArrayList;
import java.util.List;

public record JafarStackTrace(JFRStackTrace stackTrace) implements JfrStackTrace {
    @Override
    public List<? extends JfrStackFrame> frames() {
        List<? super JfrStackFrame> frames = new ArrayList<>();
        for (JFRStackFrame frame : stackTrace.frames()) {
            frames.add(new JafarStackFrame(frame));
        }
        return (List<? extends JfrStackFrame>) frames;
    }
}
