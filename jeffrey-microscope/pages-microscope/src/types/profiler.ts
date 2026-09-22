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
  methodPatterns: string[];
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

export const PROFILER_CONSTANTS = {
  selectableEvents: ['ctimer', 'cpu'] as const,
  allocUnits: ['kb', 'mb'] as const,
  lockUnits: ['us', 'ms', 's', 'm', 'h', 'd'] as const,
  intervalUnits: ['us', 'ms'] as const,
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
    lockThresholdValue: null,
    lockThresholdUnit: 'ms',
    methodPatterns: [],
    nativeMemValue: null,
    nativeMemUnit: 'mb',
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
