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

package pbouda.jeffrey.provider.profile.writer;


import pbouda.jeffrey.provider.profile.model.EventFrame;

public class StacktraceEncoder {

    public static final String DELIMITER = ";";

    private final StringBuilder builder = new StringBuilder();

    public StacktraceEncoder addFrame(EventFrame eventFrame) {
        builder.append(eventFrame.clazz()).append(DELIMITER)
                .append(eventFrame.method()).append(DELIMITER)
                .append(eventFrame.type()).append(DELIMITER)
                .append(eventFrame.bci()).append(DELIMITER)
                .append(eventFrame.line())
                .append("\n");
        return this;
    }

    public String build() {
        return builder.toString();
    }
}
