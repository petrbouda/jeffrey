/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.jfrparser.db.type;

import pbouda.jeffrey.common.model.profile.FrameType;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;

import java.util.List;

public class DbJfrStackTrace implements JfrStackTrace {

    public static final String DELIMITER = ";";
    private final long id;
    private final String frames;

    public DbJfrStackTrace(long id, String frames) {
        this.id = id;
        this.frames = frames;
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public List<? extends JfrStackFrame> frames() {
        return frames.lines()
                .map(DbJfrStackTrace::mapToStackFrame)
                .toList();
    }

    private static JfrStackFrame mapToStackFrame(String frame) {
        String[] parts = frame.split(DELIMITER);
        try {
            return new DbJfrStackFrame(
                    new DbJfrMethod(parts[0], parts[1]),
                    FrameType.fromCode(parts[2]),
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4])
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid frame: " + frame, e);
        }
    }

    @Override
    public String toString() {
        return frames;
    }
}
