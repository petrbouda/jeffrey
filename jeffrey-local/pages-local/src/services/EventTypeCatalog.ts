export interface EventTypeEntry {
  name: string
  description: string
}

export interface EventTypeCategory {
  label: string
  badge: 'jdk' | 'jeffrey' | 'profiler'
  events: EventTypeEntry[]
}

export function getEventTypePrefix(name: string): 'jdk' | 'jeffrey' | 'profiler' | 'custom' {
  if (name.startsWith('jdk.')) return 'jdk'
  if (name.startsWith('jeffrey.')) return 'jeffrey'
  if (name.startsWith('profiler.')) return 'profiler'
  return 'custom'
}

const EVENT_TYPE_CATALOG: EventTypeCategory[] = [
  // ── CPU & Execution ──
  {
    label: 'CPU & Execution',
    badge: 'jdk',
    events: [
      { name: 'jdk.CPULoad', description: 'System CPU usage' },
      { name: 'jdk.CPUTimeSample', description: 'CPU time sampling' },
      { name: 'jdk.CPUTimeSamplesLost', description: 'Lost CPU time samples' },
      { name: 'jdk.ExecutionSample', description: 'CPU sampling' },
      { name: 'jdk.NativeMethodSample', description: 'Native method sampling' },
      { name: 'jdk.ThreadCPULoad', description: 'Per-thread CPU' },
      { name: 'jdk.MethodTrace', description: 'Method tracing' },
      { name: 'jdk.MethodTiming', description: 'Method timing' }
    ]
  },
  // ── GC - Collector ──
  {
    label: 'GC - Collector',
    badge: 'jdk',
    events: [
      { name: 'jdk.GarbageCollection', description: 'GC event' },
      { name: 'jdk.YoungGarbageCollection', description: 'Young gen GC' },
      { name: 'jdk.OldGarbageCollection', description: 'Old gen GC' },
      { name: 'jdk.G1GarbageCollection', description: 'G1 collector GC' },
      { name: 'jdk.ParallelOldGarbageCollection', description: 'Parallel old GC' },
      { name: 'jdk.ZYoungGarbageCollection', description: 'ZGC young gen' },
      { name: 'jdk.ZOldGarbageCollection', description: 'ZGC old gen' },
      { name: 'jdk.SystemGC', description: 'System.gc() invocation' }
    ]
  },
  // ── GC - Heap ──
  {
    label: 'GC - Heap',
    badge: 'jdk',
    events: [
      { name: 'jdk.GCHeapSummary', description: 'Heap summary after GC' },
      { name: 'jdk.G1HeapSummary', description: 'G1 heap regions' },
      { name: 'jdk.PSHeapSummary', description: 'Parallel GC heap' },
      { name: 'jdk.MetaspaceSummary', description: 'Metaspace summary' },
      { name: 'jdk.GCHeapMemoryPoolUsage', description: 'Heap memory pool usage' },
      { name: 'jdk.GCHeapMemoryUsage', description: 'Heap memory usage' }
    ]
  },
  // ── GC - Phases ──
  {
    label: 'GC - Phases',
    badge: 'jdk',
    events: [
      { name: 'jdk.GCPhaseConcurrent', description: 'Concurrent GC phase' },
      { name: 'jdk.GCPhaseConcurrentLevel1', description: 'Concurrent phase level 1' },
      { name: 'jdk.GCPhaseConcurrentLevel2', description: 'Concurrent phase level 2' },
      { name: 'jdk.GCPhaseParallel', description: 'Parallel GC phase' },
      { name: 'jdk.GCPhasePause', description: 'GC pause phase' },
      { name: 'jdk.GCPhasePauseLevel1', description: 'Pause phase level 1' },
      { name: 'jdk.GCPhasePauseLevel2', description: 'Pause phase level 2' },
      { name: 'jdk.GCPhasePauseLevel3', description: 'Pause phase level 3' },
      { name: 'jdk.GCPhasePauseLevel4', description: 'Pause phase level 4' }
    ]
  },
  // ── GC - Configuration ──
  {
    label: 'GC - Configuration',
    badge: 'jdk',
    events: [
      { name: 'jdk.GCConfiguration', description: 'GC configuration' },
      { name: 'jdk.GCHeapConfiguration', description: 'Heap configuration' },
      { name: 'jdk.GCSurvivorConfiguration', description: 'Survivor space config' },
      { name: 'jdk.GCTLABConfiguration', description: 'TLAB configuration' },
      { name: 'jdk.YoungGenerationConfiguration', description: 'Young gen config' }
    ]
  },
  // ── GC - Detailed ──
  {
    label: 'GC - Detailed',
    badge: 'jdk',
    events: [
      { name: 'jdk.AllocationRequiringGC', description: 'Allocation requiring GC' },
      { name: 'jdk.ConcurrentModeFailure', description: 'Concurrent mode failure' },
      { name: 'jdk.EvacuationFailed', description: 'Evacuation failed' },
      { name: 'jdk.EvacuationInformation', description: 'Evacuation information' },
      { name: 'jdk.G1AdaptiveIHOP', description: 'G1 adaptive IHOP' },
      { name: 'jdk.G1BasicIHOP', description: 'G1 basic IHOP' },
      { name: 'jdk.G1EvacuationOldStatistics', description: 'G1 old evacuation stats' },
      { name: 'jdk.G1EvacuationYoungStatistics', description: 'G1 young evacuation stats' },
      { name: 'jdk.G1HeapRegionInformation', description: 'G1 heap region info' },
      { name: 'jdk.G1HeapRegionTypeChange', description: 'G1 region type change' },
      { name: 'jdk.G1MMU', description: 'G1 MMU' },
      { name: 'jdk.GCCPUTime', description: 'GC CPU time' },
      { name: 'jdk.ObjectCount', description: 'Object count' },
      { name: 'jdk.ObjectCountAfterGC', description: 'Object count after GC' },
      { name: 'jdk.PromoteObjectInNewPLAB', description: 'Promoted in new PLAB' },
      { name: 'jdk.PromoteObjectOutsidePLAB', description: 'Promoted outside PLAB' },
      { name: 'jdk.PromotionFailed', description: 'Promotion failed' },
      { name: 'jdk.ShenandoahEvacuationInformation', description: 'Shenandoah evacuation info' },
      { name: 'jdk.ShenandoahHeapRegionInformation', description: 'Shenandoah region info' },
      { name: 'jdk.ShenandoahHeapRegionStateChange', description: 'Shenandoah region state change' },
      { name: 'jdk.StringDeduplication', description: 'String deduplication' },
      { name: 'jdk.TenuringDistribution', description: 'Tenuring distribution' },
      { name: 'jdk.ZAllocationStall', description: 'ZGC allocation stall' },
      { name: 'jdk.ZPageAllocation', description: 'ZGC page allocation' },
      { name: 'jdk.ZRelocationSet', description: 'ZGC relocation set' },
      { name: 'jdk.ZRelocationSetGroup', description: 'ZGC relocation set group' },
      { name: 'jdk.ZStatisticsCounter', description: 'ZGC statistics counter' },
      { name: 'jdk.ZStatisticsSampler', description: 'ZGC statistics sampler' },
      { name: 'jdk.ZThreadDebug', description: 'ZGC thread debug' },
      { name: 'jdk.ZThreadPhase', description: 'ZGC thread phase' },
      { name: 'jdk.ZUncommit', description: 'ZGC uncommit' }
    ]
  },
  // ── GC - Reference & Metaspace ──
  {
    label: 'GC - Reference & Metaspace',
    badge: 'jdk',
    events: [
      { name: 'jdk.GCReferenceStatistics', description: 'GC reference statistics' },
      { name: 'jdk.MetaspaceAllocationFailure', description: 'Metaspace allocation failure' },
      { name: 'jdk.MetaspaceChunkFreeListSummary', description: 'Metaspace chunk free list' },
      { name: 'jdk.MetaspaceGCThreshold', description: 'Metaspace GC threshold' },
      { name: 'jdk.MetaspaceOOM', description: 'Metaspace out of memory' }
    ]
  },
  // ── Memory & Allocation ──
  {
    label: 'Memory & Allocation',
    badge: 'jdk',
    events: [
      { name: 'jdk.ObjectAllocationSample', description: 'Sampled allocations' },
      { name: 'jdk.ObjectAllocationInNewTLAB', description: 'TLAB allocation' },
      { name: 'jdk.ObjectAllocationOutsideTLAB', description: 'Outside TLAB' },
      { name: 'jdk.OldObjectSample', description: 'Old object sampling' },
      { name: 'jdk.NativeMemoryUsage', description: 'Native memory usage' },
      { name: 'jdk.NativeMemoryUsageTotal', description: 'Total native memory usage' },
      { name: 'jdk.ResidentSetSize', description: 'Resident set size' }
    ]
  },
  // ── Threading & Synchronization ──
  {
    label: 'Threading & Synchronization',
    badge: 'jdk',
    events: [
      { name: 'jdk.JavaMonitorEnter', description: 'Lock contention' },
      { name: 'jdk.JavaMonitorWait', description: 'Object.wait()' },
      { name: 'jdk.JavaMonitorDeflate', description: 'Monitor deflation' },
      { name: 'jdk.JavaMonitorInflate', description: 'Monitor inflation' },
      { name: 'jdk.JavaMonitorNotify', description: 'Monitor notify' },
      { name: 'jdk.ThreadPark', description: 'Thread parking' },
      { name: 'jdk.ThreadSleep', description: 'Thread.sleep()' },
      { name: 'jdk.ThreadStart', description: 'Thread created' },
      { name: 'jdk.ThreadEnd', description: 'Thread terminated' },
      { name: 'jdk.VirtualThreadStart', description: 'Virtual thread started' },
      { name: 'jdk.VirtualThreadEnd', description: 'Virtual thread ended' },
      { name: 'jdk.VirtualThreadPinned', description: 'Virtual thread pinned' },
      { name: 'jdk.VirtualThreadSubmitFailed', description: 'Virtual thread submit failed' }
    ]
  },
  // ── I/O ──
  {
    label: 'I/O',
    badge: 'jdk',
    events: [
      { name: 'jdk.FileRead', description: 'File read ops' },
      { name: 'jdk.FileWrite', description: 'File write ops' },
      { name: 'jdk.FileForce', description: 'File force/sync ops' },
      { name: 'jdk.SocketRead', description: 'Socket read ops' },
      { name: 'jdk.SocketWrite', description: 'Socket write ops' }
    ]
  },
  // ── Exceptions ──
  {
    label: 'Exceptions',
    badge: 'jdk',
    events: [
      { name: 'jdk.JavaErrorThrow', description: 'Error thrown' },
      { name: 'jdk.JavaExceptionThrow', description: 'Exception thrown' },
      { name: 'jdk.ExceptionStatistics', description: 'Exception statistics' }
    ]
  },
  // ── Compilation ──
  {
    label: 'Compilation',
    badge: 'jdk',
    events: [
      { name: 'jdk.Compilation', description: 'JIT compilation' },
      { name: 'jdk.CompilationFailure', description: 'Compilation failure' },
      { name: 'jdk.CompilerConfiguration', description: 'Compiler configuration' },
      { name: 'jdk.CompilerInlining', description: 'Compiler inlining' },
      { name: 'jdk.CompilerPhase', description: 'Compiler phase' },
      { name: 'jdk.CompilerQueueUtilization', description: 'Compiler queue utilization' },
      { name: 'jdk.CompilerStatistics', description: 'Compiler stats' },
      { name: 'jdk.Deoptimization', description: 'Deoptimization' },
      { name: 'jdk.JITRestart', description: 'JIT restart' }
    ]
  },
  // ── Class Loading ──
  {
    label: 'Class Loading',
    badge: 'jdk',
    events: [
      { name: 'jdk.ClassDefine', description: 'Class defined' },
      { name: 'jdk.ClassLoad', description: 'Class loaded' },
      { name: 'jdk.ClassUnload', description: 'Class unloaded' },
      { name: 'jdk.ClassRedefinition', description: 'Class redefined' },
      { name: 'jdk.RedefineClasses', description: 'Redefine classes' },
      { name: 'jdk.RetransformClasses', description: 'Retransform classes' }
    ]
  },
  // ── Code Cache ──
  {
    label: 'Code Cache',
    badge: 'jdk',
    events: [
      { name: 'jdk.CodeCacheConfiguration', description: 'Code cache configuration' },
      { name: 'jdk.CodeCacheFull', description: 'Code cache full' },
      { name: 'jdk.CodeCacheStatistics', description: 'Code cache statistics' }
    ]
  },
  // ── Runtime ──
  {
    label: 'Runtime',
    badge: 'jdk',
    events: [
      { name: 'jdk.ContinuationFreeze', description: 'Continuation freeze' },
      { name: 'jdk.ContinuationFreezeFast', description: 'Continuation freeze (fast)' },
      { name: 'jdk.ContinuationFreezeSlow', description: 'Continuation freeze (slow)' },
      { name: 'jdk.ContinuationThaw', description: 'Continuation thaw' },
      { name: 'jdk.ContinuationThawFast', description: 'Continuation thaw (fast)' },
      { name: 'jdk.ContinuationThawSlow', description: 'Continuation thaw (slow)' },
      { name: 'jdk.ExecuteVMOperation', description: 'VM operation executed' },
      { name: 'jdk.ModuleExport', description: 'Module export' },
      { name: 'jdk.ModuleRequire', description: 'Module require' },
      { name: 'jdk.NativeLibrary', description: 'Native library' },
      { name: 'jdk.NativeLibraryLoad', description: 'Native library loaded' },
      { name: 'jdk.NativeLibraryUnload', description: 'Native library unloaded' },
      { name: 'jdk.ReservedStackActivation', description: 'Reserved stack activation' },
      { name: 'jdk.SafepointBegin', description: 'Safepoint begin' },
      { name: 'jdk.SafepointEnd', description: 'Safepoint end' },
      { name: 'jdk.SafepointLatency', description: 'Safepoint latency' },
      { name: 'jdk.SafepointStateSynchronization', description: 'Safepoint state sync' },
      { name: 'jdk.Shutdown', description: 'JVM shutdown' },
      { name: 'jdk.StringTableStatistics', description: 'String table statistics' },
      { name: 'jdk.SymbolTableStatistics', description: 'Symbol table statistics' },
      { name: 'jdk.ThreadDump', description: 'Thread dump' }
    ]
  },
  // ── Application Statistics ──
  {
    label: 'Application Statistics',
    badge: 'jdk',
    events: [
      { name: 'jdk.ClassLoaderStatistics', description: 'Class loader statistics' },
      { name: 'jdk.ClassLoadingStatistics', description: 'Class loading statistics' },
      { name: 'jdk.DeprecatedInvocation', description: 'Deprecated method invocation' },
      { name: 'jdk.DirectBufferStatistics', description: 'Direct buffer statistics' },
      { name: 'jdk.FinalizerStatistics', description: 'Finalizer statistics' },
      { name: 'jdk.JavaMonitorStatistics', description: 'Monitor statistics' },
      { name: 'jdk.JavaThreadStatistics', description: 'Thread statistics' },
      { name: 'jdk.ThreadAllocationStatistics', description: 'Per-thread allocation' }
    ]
  },
  // ── Security & Serialization ──
  {
    label: 'Security & Serialization',
    badge: 'jdk',
    events: [
      { name: 'jdk.Deserialization', description: 'Object deserialization' },
      { name: 'jdk.FinalFieldMutation', description: 'Final field mutation' },
      { name: 'jdk.InitialSecurityProperty', description: 'Initial security property' },
      { name: 'jdk.SecurityPropertyModification', description: 'Security property modified' },
      { name: 'jdk.SecurityProviderService', description: 'Security provider service' },
      { name: 'jdk.SerializationMisdeclaration', description: 'Serialization misdeclaration' },
      { name: 'jdk.TLSHandshake', description: 'TLS handshake' },
      { name: 'jdk.X509Certificate', description: 'X.509 certificate' },
      { name: 'jdk.X509Validation', description: 'X.509 validation' }
    ]
  },
  // ── Operating System ──
  {
    label: 'Operating System',
    badge: 'jdk',
    events: [
      { name: 'jdk.CPUInformation', description: 'CPU details' },
      { name: 'jdk.CPUTimeStampCounter', description: 'CPU timestamp counter' },
      { name: 'jdk.ContainerCPUThrottling', description: 'Container CPU throttling' },
      { name: 'jdk.ContainerCPUUsage', description: 'Container CPU usage' },
      { name: 'jdk.ContainerConfiguration', description: 'Container configuration' },
      { name: 'jdk.ContainerIOUsage', description: 'Container I/O usage' },
      { name: 'jdk.ContainerMemoryUsage', description: 'Container memory usage' },
      { name: 'jdk.InitialEnvironmentVariable', description: 'Initial environment variable' },
      { name: 'jdk.NetworkUtilization', description: 'Network utilization' },
      { name: 'jdk.OSInformation', description: 'OS details' },
      { name: 'jdk.PhysicalMemory', description: 'Physical memory' },
      { name: 'jdk.ProcessStart', description: 'Process started' },
      { name: 'jdk.SwapSpace', description: 'Swap space usage' },
      { name: 'jdk.SystemProcess', description: 'System process' },
      { name: 'jdk.ThreadContextSwitchRate', description: 'Context switch rate' },
      { name: 'jdk.VirtualizationInformation', description: 'Virtualization info' }
    ]
  },
  // ── System Info ──
  {
    label: 'System Info',
    badge: 'jdk',
    events: [
      { name: 'jdk.JVMInformation', description: 'JVM details' },
      { name: 'jdk.InitialSystemProperty', description: 'Initial system property' }
    ]
  },
  // ── Diagnostics ──
  {
    label: 'Diagnostics',
    badge: 'jdk',
    events: [
      { name: 'jdk.HeapDump', description: 'Heap dump' },
      { name: 'jdk.JavaAgent', description: 'Java agent' },
      { name: 'jdk.NativeAgent', description: 'Native agent' },
      { name: 'jdk.SyncOnValueBasedClass', description: 'Sync on value-based class' }
    ]
  },
  // ── JVM Flags ──
  {
    label: 'JVM Flags',
    badge: 'jdk',
    events: [
      { name: 'jdk.BooleanFlag', description: 'Boolean flag value' },
      { name: 'jdk.BooleanFlagChanged', description: 'Boolean flag changed' },
      { name: 'jdk.DoubleFlag', description: 'Double flag value' },
      { name: 'jdk.DoubleFlagChanged', description: 'Double flag changed' },
      { name: 'jdk.IntFlag', description: 'Int flag value' },
      { name: 'jdk.IntFlagChanged', description: 'Int flag changed' },
      { name: 'jdk.LongFlag', description: 'Long flag value' },
      { name: 'jdk.LongFlagChanged', description: 'Long flag changed' },
      { name: 'jdk.StringFlag', description: 'String flag value' },
      { name: 'jdk.StringFlagChanged', description: 'String flag changed' },
      { name: 'jdk.UnsignedIntFlag', description: 'Unsigned int flag value' },
      { name: 'jdk.UnsignedIntFlagChanged', description: 'Unsigned int flag changed' },
      { name: 'jdk.UnsignedLongFlag', description: 'Unsigned long flag value' },
      { name: 'jdk.UnsignedLongFlagChanged', description: 'Unsigned long flag changed' }
    ]
  },
  // ── Flight Recorder ──
  {
    label: 'Flight Recorder',
    badge: 'jdk',
    events: [
      { name: 'jdk.ActiveRecording', description: 'Active recording' },
      { name: 'jdk.ActiveSetting', description: 'Active setting' },
      { name: 'jdk.DataLoss', description: 'Data loss' },
      { name: 'jdk.DumpReason', description: 'Dump reason' },
      { name: 'jdk.Flush', description: 'JFR flush' }
    ]
  },
  // ── JVM Internal ──
  {
    label: 'JVM Internal',
    badge: 'jdk',
    events: [
      { name: 'jdk.Duration', description: 'Duration event' },
      { name: 'jdk.Instant', description: 'Instant event' },
      { name: 'jdk.Text', description: 'Text event' },
      { name: 'jdk.Value', description: 'Value event' }
    ]
  },
  // ── Jeffrey: HTTP & gRPC ──
  {
    label: 'HTTP & gRPC',
    badge: 'jeffrey',
    events: [
      { name: 'jeffrey.HttpServerExchange', description: 'Inbound HTTP' },
      { name: 'jeffrey.HttpClientExchange', description: 'Outbound HTTP' },
      { name: 'jeffrey.GrpcServerExchange', description: 'Inbound gRPC' },
      { name: 'jeffrey.GrpcClientExchange', description: 'Outbound gRPC' }
    ]
  },
  // ── Jeffrey: JDBC ──
  {
    label: 'JDBC',
    badge: 'jeffrey',
    events: [
      { name: 'jeffrey.JdbcQuery', description: 'SQL queries' },
      { name: 'jeffrey.JdbcInsert', description: 'SQL inserts' },
      { name: 'jeffrey.JdbcUpdate', description: 'SQL updates' },
      { name: 'jeffrey.JdbcDelete', description: 'SQL deletes' },
      { name: 'jeffrey.JdbcExecute', description: 'SQL execute' },
      { name: 'jeffrey.JdbcStream', description: 'SQL streaming' },
      { name: 'jeffrey.JdbcPoolStatistics', description: 'Connection pool stats' }
    ]
  },
  // ── Jeffrey: Messaging ──
  {
    label: 'Messaging',
    badge: 'jeffrey',
    events: [
      { name: 'jeffrey.Message', description: 'Application messages' },
      { name: 'jeffrey.Alert', description: 'Application alerts' }
    ]
  },
  // ── Profiler ──
  {
    label: 'Profiler',
    badge: 'profiler',
    events: [
      { name: 'profiler.WallClockSample', description: 'Wall clock sampling' },
      { name: 'profiler.Malloc', description: 'Native allocations' },
      { name: 'profiler.Free', description: 'Native deallocations' },
      { name: 'profiler.LiveObject', description: 'Live objects' }
    ]
  }
]

export default EVENT_TYPE_CATALOG
