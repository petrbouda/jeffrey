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

package pbouda.jeffrey.shared.model;

public abstract class EventTypeName {

    public static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
    public static final String METHOD_TRACE = "jdk.MethodTrace";
    public static final String WALL_CLOCK_SAMPLE = "profiler.WallClockSample";
    public static final String MALLOC = "profiler.Malloc";
    public static final String FREE = "profiler.Free";
    public static final String NATIVE_LEAK = "jeffrey.NativeLeak";
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
    
    // GC Events
    public static final String GARBAGE_COLLECTION = "jdk.GarbageCollection";
    public static final String GC_HEAP_SUMMARY = "jdk.GCHeapSummary";
    public static final String G1_HEAP_SUMMARY = "jdk.G1HeapSummary";
    public static final String PS_HEAP_SUMMARY = "jdk.PSHeapSummary";
    public static final String YOUNG_GARBAGE_COLLECTION = "jdk.YoungGarbageCollection";
    public static final String OLD_GARBAGE_COLLECTION = "jdk.OldGarbageCollection";
    public static final String PARALLEL_OLD_GARBAGE_COLLECTION = "jdk.ParallelOldGarbageCollection";
    public static final String G1_GARBAGE_COLLECTION = "jdk.G1GarbageCollection";
    public static final String Z_YOUNG_GARBAGE_COLLECTION = "jdk.ZYoungGarbageCollection";
    public static final String Z_OLD_GARBAGE_COLLECTION = "jdk.ZOldGarbageCollection";
    public static final String GC_PHASE_CONCURRENT = "jdk.GCPhaseConcurrent";

    public static final String YOUNG_GENERATION_CONFIGURATION = "jdk.YoungGenerationConfiguration";
    public static final String COMPILER_CONFIGURATION = "jdk.CompilerConfiguration";
    public static final String JVM_INFORMATION = "jdk.JVMInformation";
    public static final String CPU_INFORMATION = "jdk.CPUInformation";
    public static final String OS_INFORMATION = "jdk.OSInformation";
    public static final String VIRTUALIZATION_INFORMATION = "jdk.VirtualizationInformation";
    public static final String JAVA_THREAD_STATISTICS = "jdk.JavaThreadStatistics";
    public static final String THREAD_ALLOCATION_STATISTICS = "jdk.ThreadAllocationStatistics";
    public static final String THREAD_CPU_LOAD = "jdk.ThreadCPULoad";
    public static final String COMPILER_STATISTICS = "jdk.CompilerStatistics";
    public static final String COMPILATION = "jdk.Compilation";

    // ----------------------------
    // Application events - JEFFREY
    // ----------------------------

    // JDBC POOL events
    public static final String JDBC_POOL_STATISTICS = "jeffrey.JdbcPoolStatistics";
    public static final String ACQUIRING_POOLED_JDBC_CONNECTION_TIMEOUT = "jeffrey.AcquiringPooledJdbcConnectionTimeout";
    public static final String POOLED_JDBC_CONNECTION_ACQUIRED = "jeffrey.PooledJdbcConnectionAcquired";
    public static final String POOLED_JDBC_CONNECTION_BORROWED = "jeffrey.PooledJdbcConnectionBorrowed";
    public static final String POOLED_JDBC_CONNECTION_CREATED = "jeffrey.PooledJdbcConnectionCreated";

    // JDBC events
    public static final String JDBC_INSERT = "jeffrey.JdbcInsert";
    public static final String JDBC_UPDATE = "jeffrey.JdbcUpdate";
    public static final String JDBC_DELETE = "jeffrey.JdbcDelete";
    public static final String JDBC_QUERY = "jeffrey.JdbcQuery";
    public static final String JDBC_EXECUTE = "jeffrey.JdbcExecute";
    public static final String JDBC_STREAM = "jeffrey.JdbcStream";

    // HTTP events
    public static final String HTTP_SERVER_EXCHANGE = "jeffrey.HttpServerExchange";
    public static final String HTTP_CLIENT_EXCHANGE = "jeffrey.HttpClientExchange";

    // Streaming events
    public static final String IMPORTANT_MESSAGE = "jeffrey.ImportantMessage";

    // Container events
    public static final String CONTAINER_CONFIGURATION = "jdk.ContainerConfiguration";
    public static final String CONTAINER_CPU_THROTTLING = "jdk.ContainerCPUThrottling";
    public static final String CONTAINER_MEMORY_USAGE = "jdk.ContainerMemoryUsage";
    public static final String CONTAINER_IO_USAGE = "jdk.ContainerIOUsage";
}
