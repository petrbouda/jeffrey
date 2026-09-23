/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { ref, watch, computed } from 'vue';
import type {
  ProfilerConfig,
  OptionStates,
  ConfigToken,
  MethodTraceTarget
} from '@/types/profiler';
import {
  DEFAULT_AGENT_PATH,
  DEFAULT_LOCK_THRESHOLD,
  DEFAULT_OUTPUT_FILES,
  DEFAULT_TRACE_LATENCY,
  PROFILER_CONSTANTS,
  defaultProfilerConfig
} from '@/types/profiler';
import {
  allocOption,
  lockOption,
  nativeMemOption,
  normalizeThreshold,
  traceOption,
  tracePatternError
} from '@/composables/profilerOptions';
import {
  BYTE_INTERVAL_UNITS,
  DURATION_THRESHOLD_UNITS,
  SAMPLING_INTERVAL_UNITS,
  isOffered
} from '@/composables/profilerUnits';

/** The outcome of adding a traced method: added, or refused with the reason to show the user. */
export type AddMethodTraceResult = { added: true } | { added: false; error: string };

/**
 * Ids for traced-method rows, unique for the page's lifetime. The list is keyed by them, so removing
 * a row cannot hand its neighbour's half-typed threshold to the next row the way an index key does.
 */
let lastMethodTraceId = 0;

function nextMethodTraceId(): number {
  lastMethodTraceId += 1;
  return lastMethodTraceId;
}

function hasPattern(target: MethodTraceTarget): boolean {
  return target.pattern.trim().length > 0;
}

export function useProfilerConfig() {
  const config = ref<ProfilerConfig>(defaultProfilerConfig());

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
        if (!isOffered(BYTE_INTERVAL_UNITS, config.value.allocUnit)) {
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
        if (!isOffered(DURATION_THRESHOLD_UNITS, config.value.lockThresholdUnit)) {
          config.value.lockThresholdUnit = DEFAULT_LOCK_THRESHOLD.unit;
        }
      }
    }
  );

  // The output field follows the source while it still holds the previous source's default, so a
  // Jeffrey JIB command writes into the session and a custom one into /tmp; a typed path is kept.
  watch(
    () => config.value.profilerSource,
    (source, previous) => {
      const file = config.value.file.trim();
      if (!file || file === DEFAULT_OUTPUT_FILES[previous]) {
        config.value.file = DEFAULT_OUTPUT_FILES[source];
      }
    }
  );

  // Validation watchers
  watch(
    () => config.value.allocUnit,
    unit => {
      if (!isOffered(BYTE_INTERVAL_UNITS, unit)) {
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
      if (!isOffered(SAMPLING_INTERVAL_UNITS, unit)) {
        config.value.intervalUnit = 'ms';
      }
    }
  );

  // Threshold fields are number inputs, and a cleared one stores '' rather than null. Keep the
  // stored value a number or null, so the type on ProfilerConfig is what the fields really hold.
  watch(
    () => config.value.lockThresholdValue,
    value => {
      const normalized = normalizeThreshold(value);
      if (normalized !== value) {
        config.value.lockThresholdValue = normalized;
      }
    }
  );

  watch(
    () => config.value.nativeMemValue,
    value => {
      const normalized = normalizeThreshold(value);
      if (normalized !== value) {
        config.value.nativeMemValue = normalized;
      }
    }
  );

  watch(
    () => config.value.methodTraces,
    traces => {
      traces.forEach(target => {
        const normalized = normalizeThreshold(target.latencyValue);
        if (normalized !== target.latencyValue) {
          target.latencyValue = normalized;
        }
      });
    },
    { deep: true }
  );

  /** The literal path a custom-profiler command carries; a blank field means the placeholder. */
  const resolveAgentPath = (): string => {
    const custom = config.value.agentPathCustom.trim();
    return custom ? custom : DEFAULT_AGENT_PATH;
  };

  /**
   * What goes in front of the options. Nothing for Jeffrey JIB: Jeffrey Provisioner supplies the
   * library itself. A custom profiler names its library as `-agentpath:<path>=`.
   */
  const resolveAgentPrefix = (): string => {
    if (config.value.profilerSource === 'jeffrey-jib') {
      return '';
    }
    return `-agentpath:${resolveAgentPath()}=`;
  };

  /** The output file the command carries; a cleared or whitespace-only field means the default. */
  const resolveOutputFile = (): string => {
    const custom = config.value.file.trim();
    return custom ? custom : DEFAULT_OUTPUT_FILES[config.value.profilerSource];
  };

  const builderTokens = computed((): ConfigToken[] => {
    const tokens: ConfigToken[] = [
      {
        key: 'agent',
        label: 'Agent',
        value: `${resolveAgentPrefix()}start`
      }
    ];

    if (optionStates.value.alloc) {
      tokens.push({
        key: 'alloc',
        label: 'Alloc',
        value: allocOption(config.value.allocValue, config.value.allocUnit)
      });
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
      config.value.methodTraces.filter(hasPattern).forEach(target => {
        tokens.push({
          key: `methodTracing${target.id}`,
          label: 'Method Tracing',
          value: traceOption(target)
        });
      });
    }

    if (optionStates.value.nativeMem) {
      tokens.push({
        key: 'nativeMem',
        label: 'Native Memory',
        value: nativeMemOption(config.value.nativeMemValue, config.value.nativeMemUnit)
      });

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
    const parts = [`${resolveAgentPrefix()}start`];

    if (optionStates.value.alloc) {
      parts.push(allocOption(config.value.allocValue, config.value.allocUnit));
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
      parts.push(nativeMemOption(config.value.nativeMemValue, config.value.nativeMemUnit));

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

  /**
   * Adds a method at the default latency (every call); its row is where a threshold is set.
   * A pattern async-profiler would reject is refused, since one bad target fails the whole start.
   */
  const addMethodTrace = (pattern: string): AddMethodTraceResult => {
    const error = tracePatternError(pattern);
    if (error !== null) {
      return { added: false, error };
    }
    config.value.methodTraces.push({
      id: nextMethodTraceId(),
      pattern: pattern.trim(),
      latencyValue: DEFAULT_TRACE_LATENCY.value,
      latencyUnit: DEFAULT_TRACE_LATENCY.unit
    });
    return { added: true };
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
    removeMethodTrace
  };
}
