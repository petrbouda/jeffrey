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

package pbouda.jeffrey.frameir.processor.filter;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import pbouda.jeffrey.common.ThreadInfo;

public class IncludeSingleThreadOnlyFilter implements EventProcessorFilter {

    private final ThreadInfo threadInfo;

    public IncludeSingleThreadOnlyFilter(ThreadInfo threadInfo) {
        this.threadInfo = threadInfo;
    }

    @Override
    public boolean test(RecordedEvent event) {
        RecordedThread thread = event.getThread();
        if (thread == null && event.hasField("sampledThread")) {
            thread = event.getThread("sampledThread");
        }
        if (thread == null) {
            return false;
        }

        return threadInfo.osId() == thread.getOSThreadId();
    }
}
