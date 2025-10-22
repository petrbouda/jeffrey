export interface ProfilerConfig {
  agentPath: string;
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

export const PROFILER_CONSTANTS = {
  selectableEvents: ['ctimer', 'cpu'] as const,
  allocUnits: ['kb', 'mb'] as const,
  lockUnits: ['us', 'ms', 's', 'm', 'h', 'd'] as const,
  intervalUnits: ['us', 'ms'] as const,
  defaultConfig: {
    agentPath: '${JEFFREY_PROFILER}',
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
    file: '%{JEFFREY_CURRENT_SESSION}/profile-%t.jfr'
  } as ProfilerConfig
} as const;
