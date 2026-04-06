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
  {
    label: 'CPU & Execution',
    badge: 'jdk',
    events: [
      { name: 'jdk.CPULoad', description: 'System CPU usage' },
      { name: 'jdk.ExecutionSample', description: 'CPU sampling' },
      { name: 'jdk.NativeMethodSample', description: 'Native method sampling' },
      { name: 'jdk.ThreadCPULoad', description: 'Per-thread CPU' },
      { name: 'jdk.MethodTrace', description: 'Method tracing' }
    ]
  },
  {
    label: 'Garbage Collection',
    badge: 'jdk',
    events: [
      { name: 'jdk.GarbageCollection', description: 'GC event' },
      { name: 'jdk.GCHeapSummary', description: 'Heap summary after GC' },
      { name: 'jdk.G1HeapSummary', description: 'G1 heap regions' },
      { name: 'jdk.PSHeapSummary', description: 'Parallel GC heap' },
      { name: 'jdk.YoungGarbageCollection', description: 'Young gen GC' },
      { name: 'jdk.OldGarbageCollection', description: 'Old gen GC' },
      { name: 'jdk.G1GarbageCollection', description: 'G1 collector GC' },
      { name: 'jdk.ZYoungGarbageCollection', description: 'ZGC young gen' },
      { name: 'jdk.ZOldGarbageCollection', description: 'ZGC old gen' },
      { name: 'jdk.GCPhaseConcurrent', description: 'Concurrent GC phase' }
    ]
  },
  {
    label: 'Memory & Allocation',
    badge: 'jdk',
    events: [
      { name: 'jdk.ObjectAllocationSample', description: 'Sampled allocations' },
      { name: 'jdk.ObjectAllocationInNewTLAB', description: 'TLAB allocation' },
      { name: 'jdk.ObjectAllocationOutsideTLAB', description: 'Outside TLAB' },
      { name: 'jdk.ThreadAllocationStatistics', description: 'Per-thread allocation' }
    ]
  },
  {
    label: 'Threading & Synchronization',
    badge: 'jdk',
    events: [
      { name: 'jdk.JavaMonitorEnter', description: 'Lock contention' },
      { name: 'jdk.JavaMonitorWait', description: 'Object.wait()' },
      { name: 'jdk.ThreadPark', description: 'Thread parking' },
      { name: 'jdk.ThreadSleep', description: 'Thread.sleep()' },
      { name: 'jdk.ThreadStart', description: 'Thread created' },
      { name: 'jdk.ThreadEnd', description: 'Thread terminated' }
    ]
  },
  {
    label: 'I/O',
    badge: 'jdk',
    events: [
      { name: 'jdk.FileRead', description: 'File read ops' },
      { name: 'jdk.FileWrite', description: 'File write ops' },
      { name: 'jdk.SocketRead', description: 'Socket read ops' },
      { name: 'jdk.SocketWrite', description: 'Socket write ops' }
    ]
  },
  {
    label: 'Compilation',
    badge: 'jdk',
    events: [
      { name: 'jdk.Compilation', description: 'JIT compilation' },
      { name: 'jdk.CompilerPhase', description: 'Compiler phase' },
      { name: 'jdk.CompilerStatistics', description: 'Compiler stats' }
    ]
  },
  {
    label: 'System Info',
    badge: 'jdk',
    events: [
      { name: 'jdk.JVMInformation', description: 'JVM details' },
      { name: 'jdk.CPUInformation', description: 'CPU details' },
      { name: 'jdk.OSInformation', description: 'OS details' },
      { name: 'jdk.ActiveRecording', description: 'Active recordings' }
    ]
  },
  {
    label: 'Container',
    badge: 'jdk',
    events: [
      { name: 'jdk.ContainerCPUThrottling', description: 'CPU throttling' },
      { name: 'jdk.ContainerMemoryUsage', description: 'Memory usage' },
      { name: 'jdk.ContainerIOUsage', description: 'I/O usage' }
    ]
  },
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
  {
    label: 'Messaging',
    badge: 'jeffrey',
    events: [
      { name: 'jeffrey.Message', description: 'Application messages' },
      { name: 'jeffrey.Alert', description: 'Application alerts' }
    ]
  },
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
