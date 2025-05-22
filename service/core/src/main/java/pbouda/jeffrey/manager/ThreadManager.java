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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.profile.thread.ThreadRoot;
import pbouda.jeffrey.resources.project.profile.ThreadResource;

import java.util.List;
import java.util.function.Function;

public interface ThreadManager {

    /**
     * A thread that is allocating memory.
     *
     * @param osId the OS thread ID
     * @param javaId the Java thread ID
     * @param name the name of the thread
     * @param allocatedBytes the number of bytes allocated by this thread
     */
    record AllocatingThread(long osId, long javaId, String name, long allocatedBytes) {
    }

    /**
     * Different thread counts for gauge visualization on UI.
     *
     * @param accumulated the total number of threads created since the JVM started
     * @param peak the peak number of threads created since the JVM started
     * @param maxActive the max of active threads
     * @param maxDaemon the max of daemon threads
     */
    record ThreadStats(long accumulated, long peak, long maxActive, long maxDaemon) {
    }


    ThreadStats threadStatistics();

    long[][] activeGraphPoints();

    List<AllocatingThread> threadsAllocatingMemory();

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, ThreadManager> {
    }

    ThreadRoot threadRows();
}
