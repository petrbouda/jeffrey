export interface ProfilerConfig {
  agentPathCustom: string;
  event: string;
  wallValue: number | null;
  wallUnit: string;
  loopValue: number;
  loopUnit: string;
  intervalValue: number | null;
  intervalUnit: string;
  allocThresholdEnabled: boolean;
  allocValue: number | null;
  allocUnit: string;
  lockThresholdEnabled: boolean;
  lockThresholdValue: number | null;
  lockThresholdUnit: string;
  methodTraces: MethodTraceTarget[];
  nativeMemValue: number | null;
  nativeMemUnit: string;
  nativeMemOmitFree: boolean;
  chunksizeValue: number;
  chunksizeUnit: string;
  chunktimeValue: number;
  chunktimeUnit: string;
  jfrsync: string;
  jfrsyncFile: string;
  jfcMode: string;
  file: string;
}

/**
 * One `trace=` target. Without a latency every call is recorded; with one, async-profiler keeps
 * only the calls that took at least that long (`trace=Class.method:5ms`).
 */
export interface MethodTraceTarget {
  pattern: string;
  latencyValue: number | null;
  latencyUnit: string;
}

export interface OptionStates {
  event: boolean;
  alloc: boolean;
  lock: boolean;
  wall: boolean;
  methodTracing: boolean;
  nativeMem: boolean;
  chunksize: boolean;
  chunktime: boolean;
  jfrsync: boolean;
}

export interface ConfigToken {
  key: string;
  label: string;
  value: string;
}

export interface ConfigCardDefinition {
  id: keyof OptionStates;
  title: string;
  subtitle: string;
  icon: string;
  cardType: 'required' | 'optional';
  component?: string;
}

/** Placeholder for the Agent Path field, and the path used when it is left blank. */
export const DEFAULT_AGENT_PATH = '/path/to/libasyncProfiler.so';

/** Where a copied command writes its recordings when the output field is left blank. */
export const DEFAULT_OUTPUT_FILE = '/tmp/profile-%t.jfr';

/**
 * The builder starts lock profiling at 0, every contention. It is always written out as `lock=0`:
 * a bare `lock` would mean async-profiler's own 10 µs default instead.
 */
export const DEFAULT_LOCK_THRESHOLD = { value: 0, unit: 'us' } as const;

/** What `all` uses for native memory. A bare `nativemem` would record every malloc. */
export const DEFAULT_NATIVE_MEM_INTERVAL = { value: 512, unit: 'kb' } as const;

/** A newly traced method records every call (`trace=M`) until a threshold is set on its row. */
export const DEFAULT_TRACE_LATENCY = { value: 0, unit: 'ms' } as const;

export const PROFILER_CONSTANTS = {
  selectableEvents: ['ctimer', 'cpu'] as const,
  allocUnits: ['kb', 'mb'] as const,
  // Lock thresholds are parsed with async-profiler's NANOS table, where 'm' means milli.
  lockUnits: ['us', 'ms', 's'] as const,
  intervalUnits: ['us', 'ms'] as const,
  // Parsed with async-profiler's NANOS table: n, u, m (milli), s. A bare number means nanoseconds.
  traceLatencyUnits: ['us', 'ms', 's'] as const,
  defaultConfig: {
    agentPathCustom: '',
    event: 'ctimer',
    wallValue: null,
    wallUnit: 'ms',
    loopValue: 15,
    loopUnit: 'm',
    intervalValue: null,
    intervalUnit: 'ms',
    allocThresholdEnabled: false,
    allocValue: null,
    allocUnit: 'MB',
    lockThresholdEnabled: false,
    lockThresholdValue: DEFAULT_LOCK_THRESHOLD.value,
    lockThresholdUnit: DEFAULT_LOCK_THRESHOLD.unit,
    methodTraces: [],
    nativeMemValue: DEFAULT_NATIVE_MEM_INTERVAL.value,
    nativeMemUnit: DEFAULT_NATIVE_MEM_INTERVAL.unit,
    nativeMemOmitFree: false,
    chunksizeValue: 5,
    chunksizeUnit: 'm',
    chunktimeValue: 1,
    chunktimeUnit: 'h',
    jfrsync: 'default',
    jfrsyncFile: '',
    jfcMode: 'default',
    file: DEFAULT_OUTPUT_FILE
  } as ProfilerConfig
} as const;
