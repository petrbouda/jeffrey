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

import jdk.jfr.consumer.RecordedThread;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;

public record JdkThread(RecordedThread thread) implements JfrThread {
    @Override
    public long osThreadId() {
        return thread.getOSThreadId();
    }

    @Override
    public long javaThreadId() {
        return thread.getJavaThreadId();
    }

    @Override
    public String osName() {
        return thread.getOSName();
    }

    @Override
    public String javaName() {
        return thread.getJavaName();
    }

    @Override
    public boolean virtual() {
        return thread.isVirtual();
    }
}