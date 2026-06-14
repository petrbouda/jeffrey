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

package cafe.jeffrey.shared.common.model;

public abstract class EventTypeName {

    public static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
    public static final String CPU_TIME_SAMPLE = "jdk.CPUTimeSample";
    public static final String CPU_TIME_SAMPLES_LOST = "jdk.CPUTimeSamplesLost";
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
    public static final String OLD_OBJECT_SAMPLE = "jdk.OldObjectSample";
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
    public static final String TENURING_DISTRIBUTION = "jdk.TenuringDistribution";
    public static final String GC_REFERENCE_STATISTICS = "jdk.GCReferenceStatistics";
    public static final String GC_CPU_TIME = "jdk.GCCPUTime";
    public static final String G1_ADAPTIVE_IHOP = "jdk.G1AdaptiveIHOP";
    public static final String G1_BASIC_IHOP = "jdk.G1BasicIHOP";
    public static final String G1_MMU = "jdk.G1MMU";
    public static final String STRING_TABLE_STATISTICS = "jdk.StringTableStatistics";
    public static final String SYMBOL_TABLE_STATISTICS = "jdk.SymbolTableStatistics";
    public static final String FINALIZER_STATISTICS = "jdk.FinalizerStatistics";
    public static final String STRING_DEDUPLICATION = "jdk.StringDeduplication";

    // G1 deep-dive events
    public static final String G1_HEAP_REGION_INFORMATION = "jdk.G1HeapRegionInformation";
    public static final String G1_HEAP_REGION_TYPE_CHANGE = "jdk.G1HeapRegionTypeChange";
    public static final String EVACUATION_INFORMATION = "jdk.EvacuationInformation";
    public static final String EVACUATION_FAILED = "jdk.EvacuationFailed";
    public static final String GC_PHASE_PAUSE = "jdk.GCPhasePause";
    public static final String GC_PHASE_PAUSE_LEVEL_1 = "jdk.GCPhasePauseLevel1";
    public static final String GC_PHASE_PAUSE_LEVEL_2 = "jdk.GCPhasePauseLevel2";
    public static final String GC_PHASE_PAUSE_LEVEL_3 = "jdk.GCPhasePauseLevel3";
    public static final String GC_PHASE_PAUSE_LEVEL_4 = "jdk.GCPhasePauseLevel4";
    public static final String GC_PHASE_PARALLEL = "jdk.GCPhaseParallel";
    public static final String SYSTEM_GC = "jdk.SystemGC";
    public static final String GC_LOCKER = "jdk.GCLocker";

    // ZGC deep-dive events
    public static final String Z_ALLOCATION_STALL = "jdk.ZAllocationStall";
    public static final String Z_PAGE_ALLOCATION = "jdk.ZPageAllocation";
    public static final String Z_RELOCATION_SET = "jdk.ZRelocationSet";
    public static final String Z_RELOCATION_SET_GROUP = "jdk.ZRelocationSetGroup";
    public static final String Z_UNCOMMIT = "jdk.ZUncommit";
    public static final String Z_THREAD_PHASE = "jdk.ZThreadPhase";

    public static final String YOUNG_GENERATION_CONFIGURATION = "jdk.YoungGenerationConfiguration";
    public static final String COMPILER_CONFIGURATION = "jdk.CompilerConfiguration";
    public static final String JVM_INFORMATION = "jdk.JVMInformation";
    public static final String CPU_INFORMATION = "jdk.CPUInformation";
    public static final String OS_INFORMATION = "jdk.OSInformation";
    public static final String VIRTUALIZATION_INFORMATION = "jdk.VirtualizationInformation";
    public static final String SHUTDOWN = "jdk.Shutdown";
    public static final String JAVA_THREAD_STATISTICS = "jdk.JavaThreadStatistics";
    public static final String THREAD_ALLOCATION_STATISTICS = "jdk.ThreadAllocationStatistics";
    public static final String THREAD_CPU_LOAD = "jdk.ThreadCPULoad";
    public static final String COMPILER_STATISTICS = "jdk.CompilerStatistics";
    public static final String COMPILATION = "jdk.Compilation";
    public static final String DEOPTIMIZATION = "jdk.Deoptimization";
    public static final String COMPILER_QUEUE_UTILIZATION = "jdk.CompilerQueueUtilization";
    public static final String CODE_CACHE_STATISTICS = "jdk.CodeCacheStatistics";
    public static final String CODE_CACHE_FULL = "jdk.CodeCacheFull";
    public static final String SAFEPOINT_BEGIN = "jdk.SafepointBegin";
    public static final String SAFEPOINT_STATE_SYNCHRONIZATION = "jdk.SafepointStateSynchronization";
    public static final String SAFEPOINT_END = "jdk.SafepointEnd";
    public static final String EXECUTE_VM_OPERATION = "jdk.ExecuteVMOperation";
    public static final String JAVA_MONITOR_INFLATE = "jdk.JavaMonitorInflate";
    public static final String VIRTUAL_THREAD_PINNED = "jdk.VirtualThreadPinned";
    public static final String VIRTUAL_THREAD_START = "jdk.VirtualThreadStart";
    public static final String VIRTUAL_THREAD_END = "jdk.VirtualThreadEnd";
    public static final String VIRTUAL_THREAD_SUBMIT_FAILED = "jdk.VirtualThreadSubmitFailed";

    // System & host events
    public static final String CPU_LOAD = "jdk.CPULoad";
    public static final String NETWORK_UTILIZATION = "jdk.NetworkUtilization";
    public static final String THREAD_CONTEXT_SWITCH_RATE = "jdk.ThreadContextSwitchRate";
    public static final String SYSTEM_PROCESS = "jdk.SystemProcess";

    // Native memory events
    public static final String RESIDENT_SET_SIZE = "jdk.ResidentSetSize";
    public static final String DIRECT_BUFFER_STATISTICS = "jdk.DirectBufferStatistics";
    public static final String NATIVE_LIBRARY = "jdk.NativeLibrary";
    public static final String NATIVE_MEMORY_USAGE = "jdk.NativeMemoryUsage";
    public static final String NATIVE_MEMORY_USAGE_TOTAL = "jdk.NativeMemoryUsageTotal";

    // Exception events
    public static final String EXCEPTION_STATISTICS = "jdk.ExceptionStatistics";
    public static final String JAVA_EXCEPTION_THROW = "jdk.JavaExceptionThrow";
    public static final String JAVA_ERROR_THROW = "jdk.JavaErrorThrow";

    // Class Loading events
    public static final String CLASS_LOADING_STATISTICS = "jdk.ClassLoadingStatistics";
    public static final String CLASS_LOADER_STATISTICS = "jdk.ClassLoaderStatistics";
    public static final String CLASS_LOAD = "jdk.ClassLoad";
    public static final String CLASS_DEFINE = "jdk.ClassDefine";
    public static final String CLASS_UNLOAD = "jdk.ClassUnload";
    public static final String CLASS_REDEFINITION = "jdk.ClassRedefinition";
    public static final String RETRANSFORM_CLASSES = "jdk.RetransformClasses";

    // Security events
    public static final String TLS_HANDSHAKE = "jdk.TLSHandshake";
    public static final String X509_CERTIFICATE = "jdk.X509Certificate";
    public static final String X509_VALIDATION = "jdk.X509Validation";
    public static final String DESERIALIZATION = "jdk.Deserialization";
    public static final String SECURITY_PROVIDER_SERVICE = "jdk.SecurityProviderService";

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

    // gRPC events
    public static final String GRPC_SERVER_EXCHANGE = "jeffrey.GrpcServerExchange";
    public static final String GRPC_CLIENT_EXCHANGE = "jeffrey.GrpcClientExchange";

    // Async-profiler events
    public static final String SPAN = "profiler.Span";

    // Streaming events
    public static final String MESSAGE = "jeffrey.Message";
    public static final String ALERT = "jeffrey.Alert";

    // Container events
    public static final String CONTAINER_CONFIGURATION = "jdk.ContainerConfiguration";
    public static final String CONTAINER_CPU_THROTTLING = "jdk.ContainerCPUThrottling";
    public static final String CONTAINER_MEMORY_USAGE = "jdk.ContainerMemoryUsage";
    public static final String CONTAINER_IO_USAGE = "jdk.ContainerIOUsage";

    // JVM Flag events
    public static final String BOOLEAN_FLAG = "jdk.BooleanFlag";
    public static final String INT_FLAG = "jdk.IntFlag";
    public static final String UNSIGNED_INT_FLAG = "jdk.UnsignedIntFlag";
    public static final String LONG_FLAG = "jdk.LongFlag";
    public static final String STRING_FLAG = "jdk.StringFlag";
}
