import { ref, watch, computed } from 'vue';
import type {
  ProfilerConfig,
  OptionStates,
  ConfigToken,
  MethodTraceTarget
} from '@/types/profiler';
import {
  DEFAULT_AGENT_PATH,
  DEFAULT_OUTPUT_FILE,
  DEFAULT_TRACE_LATENCY,
  PROFILER_CONSTANTS
} from '@/types/profiler';

/**
 * The `trace=` option for one target. The latency is appended only when it is positive:
 * async-profiler treats a missing latency as 0, i.e. every call is recorded.
 */
export function traceOption(target: MethodTraceTarget): string {
  const pattern = target.pattern.trim();
  if (target.latencyValue && target.latencyValue > 0) {
    return `trace=${pattern}:${target.latencyValue}${target.latencyUnit}`;
  }
  return `trace=${pattern}`;
}

/**
 * The `lock=` option. It is always written with a value: a bare `lock` means async-profiler's
 * 10 µs default, so an empty field is written as `lock=0` -- every contention -- which is what
 * the builder shows for it.
 */
export function lockOption(value: number | null, unit: string): string {
  if (value && value > 0) {
    return `lock=${value}${unit}`;
  }
  return 'lock=0';
}

function hasPattern(target: MethodTraceTarget): boolean {
  return target.pattern.trim().length > 0;
}

export function useProfilerConfig() {
  // The spread is shallow: the traced-method list gets its own array, or every builder would
  // push into the one held by the shared defaults.
  const config = ref<ProfilerConfig>({ ...PROFILER_CONSTANTS.defaultConfig, methodTraces: [] });

  const optionStates = ref<OptionStates>({
    event: false,
    alloc: false,
    lock: false,
    wall: false,
    methodTracing: false,
    nativeMem: false,
    chunksize: false,
    chunktime: false,
    jfrsync: false
  });

  const ensureSupportedEventValue = () => {
    if (!PROFILER_CONSTANTS.selectableEvents.includes(config.value.event as any)) {
      config.value.event = 'ctimer';
    }
  };

  // Watch for configuration validation
  watch(
    () => optionStates.value.event,
    enabled => {
      if (enabled) {
        ensureSupportedEventValue();
      }
    }
  );

  watch(
    () => optionStates.value.alloc,
    enabled => {
      if (enabled) {
        if (!PROFILER_CONSTANTS.allocUnits.includes(config.value.allocUnit as any)) {
          config.value.allocUnit = 'mb';
        }
        if (!config.value.allocThresholdEnabled) {
          config.value.allocValue = null;
        } else if (!config.value.allocValue || config.value.allocValue < 1) {
          config.value.allocValue = 64;
        }
      } else {
        config.value.allocThresholdEnabled = false;
      }
    }
  );

  watch(
    () => optionStates.value.lock,
    enabled => {
      if (enabled) {
        if (!PROFILER_CONSTANTS.lockUnits.includes(config.value.lockThresholdUnit as any)) {
          config.value.lockThresholdUnit = 'ms';
        }
      }
    }
  );

  // Validation watchers
  watch(
    () => config.value.allocUnit,
    unit => {
      if (!PROFILER_CONSTANTS.allocUnits.includes(unit as any)) {
        config.value.allocUnit = 'kb';
      }
    }
  );

  watch(
    () => config.value.allocValue,
    value => {
      if (config.value.allocThresholdEnabled && (!value || value < 1)) {
        config.value.allocValue = 1;
      }
    }
  );

  watch(
    () => config.value.allocThresholdEnabled,
    enabled => {
      if (enabled) {
        if (!config.value.allocValue || config.value.allocValue < 1) {
          config.value.allocValue = 64;
        }
      } else {
        config.value.allocValue = null;
      }
    }
  );

  watch(
    () => config.value.intervalUnit,
    unit => {
      if (!PROFILER_CONSTANTS.intervalUnits.includes(unit as any)) {
        config.value.intervalUnit = 'ms';
      }
    }
  );

  /**
   * The literal path the copied command carries. The field is free text: there is no
   * Jeffrey-managed agent mode, because the command is pasted into someone else's JVM.
   */
  const resolveAgentPath = (): string => {
    const custom = config.value.agentPathCustom.trim();
    return custom ? custom : DEFAULT_AGENT_PATH;
  };

  /** The output file the command carries; a cleared or whitespace-only field means the default. */
  const resolveOutputFile = (): string => {
    const custom = config.value.file.trim();
    return custom ? custom : DEFAULT_OUTPUT_FILE;
  };

  const builderTokens = computed((): ConfigToken[] => {
    const agentPath = resolveAgentPath();
    const tokens: ConfigToken[] = [
      {
        key: 'agent',
        label: 'Agent',
        value: `-agentpath:${agentPath}=start`
      }
    ];

    if (optionStates.value.alloc) {
      if (config.value.allocValue && config.value.allocValue > 0) {
        tokens.push({
          key: 'alloc',
          label: 'Alloc',
          value: `alloc=${config.value.allocValue}${config.value.allocUnit.toLowerCase() === 'mb' ? 'm' : 'k'}`
        });
      } else {
        tokens.push({
          key: 'alloc',
          label: 'Alloc',
          value: 'alloc'
        });
      }
    }

    if (optionStates.value.lock) {
      tokens.push({
        key: 'lock',
        label: 'Lock',
        value: lockOption(config.value.lockThresholdValue, config.value.lockThresholdUnit)
      });
    }

    if (optionStates.value.event) {
      if (config.value.intervalValue && config.value.intervalValue > 0) {
        tokens.push({
          key: 'event',
          label: 'CPU',
          value: `event=${config.value.event},interval=${config.value.intervalValue}${config.value.intervalUnit}`
        });
      } else {
        tokens.push({
          key: 'event',
          label: 'CPU',
          value: `event=${config.value.event}`
        });
      }
    }

    const loopValue =
      config.value.loopValue && config.value.loopValue >= 1 ? config.value.loopValue : 15;
    const loopUnit =
      config.value.loopValue && config.value.loopValue >= 1 ? config.value.loopUnit : 'm';
    tokens.push({
      key: 'loop',
      label: 'Loop',
      value: `loop=${loopValue}${loopUnit}`
    });

    if (optionStates.value.wall) {
      if (config.value.wallValue && config.value.wallValue > 0) {
        tokens.push({
          key: 'wall',
          label: 'Wall',
          value: `wall=${config.value.wallValue}${config.value.wallUnit}`
        });
      } else {
        tokens.push({
          key: 'wall',
          label: 'Wall',
          value: 'wall'
        });
      }
    }

    if (optionStates.value.methodTracing) {
      config.value.methodTraces.forEach((target, index) => {
        if (hasPattern(target)) {
          tokens.push({
            key: `methodTracing${index}`,
            label: 'Method Tracing',
            value: traceOption(target)
          });
        }
      });
    }

    if (optionStates.value.nativeMem) {
      if (config.value.nativeMemValue && config.value.nativeMemValue > 0) {
        tokens.push({
          key: 'nativeMem',
          label: 'Native Memory',
          value: `nativemem=${config.value.nativeMemValue}${config.value.nativeMemUnit.toLowerCase() === 'mb' ? 'm' : 'k'}`
        });
      } else {
        tokens.push({
          key: 'nativeMem',
          label: 'Native Memory',
          value: 'nativemem'
        });
      }

      if (config.value.nativeMemOmitFree) {
        tokens.push({
          key: 'nativememNoFree',
          label: 'Omit Free Events',
          value: 'nofree'
        });
      }
    }

    if (optionStates.value.jfrsync) {
      if (config.value.jfcMode === 'default') {
        // Use predefined default mode
        tokens.push({
          key: 'jfrsync',
          label: 'JFR Sync',
          value: 'jfrsync=default'
        });
      } else if (config.value.jfcMode === 'profile') {
        // Use predefined profile mode
        tokens.push({
          key: 'jfrsync',
          label: 'JFR Sync',
          value: 'jfrsync=profile'
        });
      } else if (
        config.value.jfcMode === 'custom' &&
        config.value.jfrsyncFile &&
        config.value.jfrsyncFile.trim()
      ) {
        // Use custom file path
        tokens.push({
          key: 'jfrsync',
          label: 'JFR Sync',
          value: `jfrsync=${config.value.jfrsyncFile.trim()}`
        });
      } else {
        // Fallback to default if custom is selected but no file specified
        tokens.push({
          key: 'jfrsync',
          label: 'JFR Sync',
          value: 'jfrsync=default'
        });
      }
    }

    if (optionStates.value.chunksize) {
      tokens.push({
        key: 'chunksize',
        label: 'Chunk Size',
        value: `chunksize=${config.value.chunksizeValue}${config.value.chunksizeUnit}`
      });
    }

    if (optionStates.value.chunktime) {
      tokens.push({
        key: 'chunktime',
        label: 'Chunk Time',
        value: `chunktime=${config.value.chunktimeValue}${config.value.chunktimeUnit}`
      });
    }

    tokens.push({
      key: 'file',
      label: 'Output',
      value: `file=${resolveOutputFile()}`
    });

    return tokens;
  });

  const generateFromBuilder = (): string => {
    const agentPath = resolveAgentPath();
    const parts = [`-agentpath:${agentPath}=start`];

    if (optionStates.value.alloc) {
      if (config.value.allocValue && config.value.allocValue > 0) {
        parts.push(
          `alloc=${config.value.allocValue}${config.value.allocUnit.toLowerCase() === 'mb' ? 'm' : 'k'}`
        );
      } else {
        parts.push('alloc');
      }
    }

    if (optionStates.value.lock) {
      parts.push(lockOption(config.value.lockThresholdValue, config.value.lockThresholdUnit));
    }

    if (optionStates.value.event) {
      parts.push(`event=${config.value.event}`);
      if (config.value.intervalValue && config.value.intervalValue > 0) {
        parts.push(`interval=${config.value.intervalValue}${config.value.intervalUnit}`);
      }
    }

    if (optionStates.value.wall) {
      if (config.value.wallValue && config.value.wallValue > 0) {
        parts.push(`wall=${config.value.wallValue}${config.value.wallUnit}`);
      } else {
        parts.push('wall');
      }
    }

    if (optionStates.value.methodTracing) {
      config.value.methodTraces.filter(hasPattern).forEach(target => {
        parts.push(traceOption(target));
      });
    }

    if (optionStates.value.nativeMem) {
      if (config.value.nativeMemValue && config.value.nativeMemValue > 0) {
        parts.push(
          `nativemem=${config.value.nativeMemValue}${config.value.nativeMemUnit.toLowerCase() === 'mb' ? 'm' : 'k'}`
        );
      } else {
        parts.push('nativemem');
      }

      if (config.value.nativeMemOmitFree) {
        parts.push('nofree');
      }
    }

    // Always add loop (required)
    const loopValue =
      config.value.loopValue && config.value.loopValue >= 1 ? config.value.loopValue : 15;
    const loopUnit =
      config.value.loopValue && config.value.loopValue >= 1 ? config.value.loopUnit : 'm';
    parts.push(`loop=${loopValue}${loopUnit}`);

    if (optionStates.value.jfrsync) {
      if (config.value.jfcMode === 'default') {
        // Use predefined default mode
        parts.push('jfrsync=default');
      } else if (config.value.jfcMode === 'profile') {
        // Use predefined profile mode
        parts.push('jfrsync=profile');
      } else if (
        config.value.jfcMode === 'custom' &&
        config.value.jfrsyncFile &&
        config.value.jfrsyncFile.trim()
      ) {
        // Use custom file path
        parts.push(`jfrsync=${config.value.jfrsyncFile.trim()}`);
      } else {
        // Fallback to default if custom is selected but no file specified
        parts.push('jfrsync=default');
      }
    }

    if (optionStates.value.chunksize) {
      parts.push(`chunksize=${config.value.chunksizeValue}${config.value.chunksizeUnit}`);
    }

    if (optionStates.value.chunktime) {
      parts.push(`chunktime=${config.value.chunktimeValue}${config.value.chunktimeUnit}`);
    }

    // Always add file (mandatory)
    parts.push(`file=${resolveOutputFile()}`);

    return parts.join(',');
  };

  /** Adds a method at the default latency (every call); its row is where a threshold is set. */
  const addMethodTrace = (pattern: string) => {
    if (pattern && pattern.trim()) {
      config.value.methodTraces.push({
        pattern: pattern.trim(),
        latencyValue: DEFAULT_TRACE_LATENCY.value,
        latencyUnit: DEFAULT_TRACE_LATENCY.unit
      });
    }
  };

  const removeMethodTrace = (index: number) => {
    if (index >= 0 && index < config.value.methodTraces.length) {
      config.value.methodTraces.splice(index, 1);
    }
  };

  return {
    config,
    optionStates,
    builderTokens,
    generateFromBuilder,
    addMethodTrace,
    removeMethodTrace,
    constants: PROFILER_CONSTANTS
  };
}
