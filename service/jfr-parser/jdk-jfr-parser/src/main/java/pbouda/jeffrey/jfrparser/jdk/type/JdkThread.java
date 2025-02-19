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

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;

public class JdkThread implements JfrThread {

    private final RecordedEvent event;
    private RecordedThread resolvedThread;

    public JdkThread(RecordedEvent event) {
        this.event = event;
    }

    private RecordedThread resolveThread() {
        if (resolvedThread != null) {
            return resolvedThread;
        }

        RecordedThread thread = event.getThread();
        if (thread != null) {
            resolvedThread = thread;
        } else if (event.hasField("sampledThread")) {
            resolvedThread = event.getThread("sampledThread");
        }
        return resolvedThread;
    }

    @Override
    public long osThreadId() {
        RecordedThread thread = resolveThread();
        if (thread != null) {
            return thread.getOSThreadId();
        } else {
            return -1;
        }
    }

    @Override
    public long javaThreadId() {
        RecordedThread thread = resolveThread();
        if (thread != null) {
            return thread.getJavaThreadId();
        } else {
            return -1;
        }
    }

    @Override
    public String name() {
        if (javaThreadId() > 0) {
            return javaName();
        } else {
            return osName();
        }
    }

    private String osName() {
        RecordedThread thread = resolveThread();
        if (thread != null) {
            return thread.getOSName();
        } else {
            return null;
        }
    }

    private  String javaName() {
        RecordedThread thread = resolveThread();
        if (thread != null) {
            return thread.getJavaName();
        } else {
            return null;
        }
    }
}
