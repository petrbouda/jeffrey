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

package pbouda.jeffrey.common;

public abstract class EventTypeName {

    public static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
    public static final String WALL_CLOCK_SAMPLE = "profiler.WallClockSample";
    public static final String NATIVE_MALLOC_SAMPLE = "profiler.Malloc";
    public static final String NATIVE_FREE_SAMPLE = "profiler.Free";
    public static final String JAVA_MONITOR_ENTER = "jdk.JavaMonitorEnter";
    public static final String JAVA_MONITOR_WAIT = "jdk.JavaMonitorWait";
    public static final String THREAD_START = "jdk.ThreadStart";
    public static final String THREAD_END = "jdk.ThreadEnd";
    public static final String THREAD_PARK = "jdk.ThreadPark";
    public static final String THREAD_SLEEP = "jdk.ThreadSleep";
    public static final String OBJECT_ALLOCATION_IN_NEW_TLAB = "jdk.ObjectAllocationInNewTLAB";
    public static final String OBJECT_ALLOCATION_OUTSIDE_TLAB = "jdk.ObjectAllocationOutsideTLAB";
    public static final String OBJECT_ALLOCATION_SAMPLE = "jdk.ObjectAllocationSample";
    public static final String SOCKET_READ = "jdk.SocketRead";
    public static final String SOCKET_WRITE = "jdk.SocketWrite";
    public static final String FILE_READ = "jdk.FileRead";
    public static final String FILE_WRITE = "jdk.FileWrite";
    public static final String LIVE_OBJECTS = "profiler.LiveObject";
    public static final String ACTIVE_RECORDING = "jdk.ActiveRecording";
    public static final String ACTIVE_SETTING = "jdk.ActiveSetting";
    public static final String GC_CONFIGURATION = "jdk.GCConfiguration";
    public static final String GC_HEAP_CONFIGURATION = "jdk.GCHeapConfiguration";
    public static final String GC_SURVIVOR_CONFIGURATION = "jdk.GCSurvivorConfiguration";
    public static final String GC_TLAB_CONFIGURATION = "jdk.GCTLABConfiguration";
    public static final String YOUNG_GENERATION_CONFIGURATION = "jdk.YoungGenerationConfiguration";
    public static final String COMPILER_CONFIGURATION = "jdk.CompilerConfiguration";
    public static final String CONTAINER_CONFIGURATION = "jdk.ContainerConfiguration";
    public static final String JVM_INFORMATION = "jdk.JVMInformation";
    public static final String CPU_INFORMATION = "jdk.CPUInformation";
    public static final String OS_INFORMATION = "jdk.OSInformation";
    public static final String VIRTUALIZATION_INFORMATION = "jdk.VirtualizationInformation";

}
