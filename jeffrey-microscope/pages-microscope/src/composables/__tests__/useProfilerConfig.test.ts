/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import { describe, expect, it } from 'vitest';
import { nextTick } from 'vue';
import { useProfilerConfig } from '@/composables/useProfilerConfig';
import { DEFAULT_AGENT_PATH, DEFAULT_OUTPUT_FILE } from '@/types/profiler';
import type { MethodTraceTarget } from '@/types/profiler';

const AGENT = '/opt/async-profiler/lib/libasyncProfiler.so';

/** The command is a comma-joined list; splitting it keeps assertions readable. */
function partsOf(command: string): string[] {
  return command.split(',');
}

function everyCall(pattern: string): MethodTraceTarget {
  return { pattern, latencyValue: null, latencyUnit: 'ms' };
}

describe('useProfilerConfig', () => {
  describe('generateFromBuilder', () => {
    it('emits only the mandatory options when nothing is toggled on', () => {
      const { generateFromBuilder } = useProfilerConfig();

      expect(generateFromBuilder()).toBe(
        `-agentpath:${DEFAULT_AGENT_PATH}=start,loop=15m,file=${DEFAULT_OUTPUT_FILE}`
      );
    });

    it('uses the agent path that was typed in', () => {
      const { config, generateFromBuilder } = useProfilerConfig();
      config.value.agentPathCustom = AGENT;

      expect(generateFromBuilder()).toContain(`-agentpath:${AGENT}=start`);
    });

    it('falls back to the default agent path when the field holds only whitespace', () => {
      const { config, generateFromBuilder } = useProfilerConfig();
      config.value.agentPathCustom = '   ';

      expect(generateFromBuilder()).toContain(`-agentpath:${DEFAULT_AGENT_PATH}=start`);
    });

    it('falls back to the default output file when the field is cleared', () => {
      const { config, generateFromBuilder } = useProfilerConfig();
      config.value.file = '';

      expect(generateFromBuilder()).toContain(`file=${DEFAULT_OUTPUT_FILE}`);
    });

    it('trims the output file, so a pasted path with surrounding whitespace stays a valid path', () => {
      const { config, generateFromBuilder } = useProfilerConfig();
      config.value.file = '  /var/jfr/app-%t.jfr ';

      expect(generateFromBuilder()).toContain('file=/var/jfr/app-%t.jfr');
      expect(generateFromBuilder()).not.toContain('file=  ');
    });

    it('carries no Jeffrey placeholder, since the command is pasted into another JVM', () => {
      const { generateFromBuilder } = useProfilerConfig();

      expect(generateFromBuilder()).not.toContain('<<JEFFREY:');
    });

    it('emits alloc with its threshold, converting the unit to a single letter', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.alloc = true;
      config.value.allocThresholdEnabled = true;
      config.value.allocValue = 512;
      config.value.allocUnit = 'kb';

      expect(partsOf(generateFromBuilder())).toContain('alloc=512k');
    });

    it('emits a bare alloc when no threshold is given', () => {
      const { optionStates, config, generateFromBuilder } = useProfilerConfig();
      optionStates.value.alloc = true;
      config.value.allocValue = null;

      expect(partsOf(generateFromBuilder())).toContain('alloc');
    });

    it('emits lock with its threshold', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.lock = true;
      config.value.lockThresholdValue = 10;
      config.value.lockThresholdUnit = 'ms';

      expect(partsOf(generateFromBuilder())).toContain('lock=10ms');
    });

    it('records every contention by default, written as lock=0 rather than a bare lock', () => {
      const { optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.lock = true;

      const parts = partsOf(generateFromBuilder());
      expect(parts).toContain('lock=0');
      expect(parts).not.toContain('lock');
    });

    it('emits lock=0 when the threshold is cleared, since a bare lock would still mean 10 µs', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.lock = true;
      config.value.lockThresholdValue = null;

      const parts = partsOf(generateFromBuilder());
      expect(parts).toContain('lock=0');
      expect(parts).not.toContain('lock');
    });

    it('emits the event and its interval as two separate parts', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.event = true;
      config.value.event = 'ctimer';
      config.value.intervalValue = 10;
      config.value.intervalUnit = 'ms';

      const parts = partsOf(generateFromBuilder());
      expect(parts).toContain('event=ctimer');
      expect(parts).toContain('interval=10ms');
    });

    it('emits wall with its interval', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.wall = true;
      config.value.wallValue = 50;
      config.value.wallUnit = 'ms';

      expect(partsOf(generateFromBuilder())).toContain('wall=50ms');
    });

    it('emits one trace part per method pattern', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.methodTracing = true;
      config.value.methodTraces = [
        everyCall('java.nio.ByteBuffer.allocateDirect'),
        everyCall('java.net.Socket.*')
      ];

      const parts = partsOf(generateFromBuilder());
      expect(parts).toContain('trace=java.nio.ByteBuffer.allocateDirect');
      expect(parts).toContain('trace=java.net.Socket.*');
    });

    it('appends the latency threshold to a trace pattern', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.methodTracing = true;
      config.value.methodTraces = [
        { pattern: 'com.acme.OrderService.place', latencyValue: 5, latencyUnit: 'ms' },
        { pattern: 'com.acme.Cache.get', latencyValue: 200, latencyUnit: 'us' }
      ];

      const parts = partsOf(generateFromBuilder());
      expect(parts).toContain('trace=com.acme.OrderService.place:5ms');
      expect(parts).toContain('trace=com.acme.Cache.get:200us');
    });

    it('omits the latency when it is zero, since async-profiler then records every call anyway', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.methodTracing = true;
      config.value.methodTraces = [
        { pattern: 'com.acme.OrderService.place', latencyValue: 0, latencyUnit: 'ms' }
      ];

      expect(partsOf(generateFromBuilder())).toContain('trace=com.acme.OrderService.place');
    });

    it('skips a trace target whose pattern is blank', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.methodTracing = true;
      config.value.methodTraces = [{ pattern: '   ', latencyValue: 5, latencyUnit: 'ms' }];

      expect(generateFromBuilder()).not.toContain('trace=');
    });

    it('adds a trimmed trace target that records every call by default', () => {
      const { config, optionStates, addMethodTrace, generateFromBuilder } = useProfilerConfig();
      optionStates.value.methodTracing = true;
      addMethodTrace('  com.acme.OrderService.place  ');

      expect(config.value.methodTraces).toHaveLength(1);
      expect(partsOf(generateFromBuilder())).toContain('trace=com.acme.OrderService.place');
    });

    it('keeps traced methods per builder instead of sharing the default list', () => {
      const first = useProfilerConfig();
      first.addMethodTrace('com.acme.OrderService.place');

      const second = useProfilerConfig();
      expect(second.config.value.methodTraces).toHaveLength(0);
    });

    it('refuses a trace target async-profiler would reject and says why', () => {
      const { config, addMethodTrace } = useProfilerConfig();

      const result = addMethodTrace('G1CollectedHeap::humongous_obj_allocate');

      expect(result.added).toBe(false);
      if (!result.added) {
        expect(result.error).toContain('Class.method');
      }
      expect(config.value.methodTraces).toHaveLength(0);
    });

    it('writes a fractional trace latency as an exact integer in a smaller unit', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.methodTracing = true;
      config.value.methodTraces = [
        { pattern: 'com.acme.OrderService.place', latencyValue: 0.5, latencyUnit: 'ms' }
      ];

      expect(partsOf(generateFromBuilder())).toContain('trace=com.acme.OrderService.place:500us');
    });

    it('writes a fractional lock threshold as an exact integer in a smaller unit', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.lock = true;
      config.value.lockThresholdValue = 1.5;
      config.value.lockThresholdUnit = 'ms';

      expect(partsOf(generateFromBuilder())).toContain('lock=1500us');
    });

    it('stores a cleared threshold field as null, not the empty string the input hands over', async () => {
      const { config, addMethodTrace } = useProfilerConfig();
      addMethodTrace('com.acme.OrderService.place');

      // A cleared <input type="number"> hands v-model.number the empty string.
      config.value.lockThresholdValue = '' as unknown as number;
      config.value.nativeMemValue = '' as unknown as number;
      config.value.methodTraces[0].latencyValue = '' as unknown as number;
      await nextTick();

      expect(config.value.lockThresholdValue).toBeNull();
      expect(config.value.nativeMemValue).toBeNull();
      expect(config.value.methodTraces[0].latencyValue).toBeNull();
    });

    it('stores a negative threshold as null, matching the badge that says it records everything', async () => {
      const { config } = useProfilerConfig();

      config.value.lockThresholdValue = -5;
      await nextTick();

      expect(config.value.lockThresholdValue).toBeNull();
    });

    it('samples native memory every 512 KiB by default', () => {
      const { optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.nativeMem = true;

      expect(partsOf(generateFromBuilder())).toContain('nativemem=512k');
    });

    it('emits a bare nativemem, recording every malloc, when the interval is cleared', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.nativeMem = true;
      config.value.nativeMemValue = null;

      expect(partsOf(generateFromBuilder())).toContain('nativemem');
    });

    it('emits nativemem followed by nofree when free tracking is omitted', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.nativeMem = true;
      config.value.nativeMemValue = 4;
      config.value.nativeMemUnit = 'mb';
      config.value.nativeMemOmitFree = true;

      const parts = partsOf(generateFromBuilder());
      expect(parts).toContain('nativemem=4m');
      expect(parts.indexOf('nofree')).toBe(parts.indexOf('nativemem=4m') + 1);
    });

    it.each([
      ['default', 'jfrsync=default'],
      ['profile', 'jfrsync=profile']
    ])('emits the predefined %s JFC mode', (mode, expected) => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.jfrsync = true;
      config.value.jfcMode = mode;

      expect(partsOf(generateFromBuilder())).toContain(expected);
    });

    it('emits a custom JFC path when one is given', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.jfrsync = true;
      config.value.jfcMode = 'custom';
      config.value.jfrsyncFile = '/etc/jeffrey/custom.jfc';

      expect(partsOf(generateFromBuilder())).toContain('jfrsync=/etc/jeffrey/custom.jfc');
    });

    it('falls back to the default JFC mode when custom is chosen without a path', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.jfrsync = true;
      config.value.jfcMode = 'custom';
      config.value.jfrsyncFile = '';

      expect(partsOf(generateFromBuilder())).toContain('jfrsync=default');
    });

    it('emits chunk size and chunk time', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      optionStates.value.chunksize = true;
      config.value.chunksizeValue = 100;
      config.value.chunksizeUnit = 'm';
      optionStates.value.chunktime = true;
      config.value.chunktimeValue = 1;
      config.value.chunktimeUnit = 'h';

      const parts = partsOf(generateFromBuilder());
      expect(parts).toContain('chunksize=100m');
      expect(parts).toContain('chunktime=1h');
    });

    it('falls back to a 15m loop when the interval is cleared', () => {
      const { config, generateFromBuilder } = useProfilerConfig();
      config.value.loopValue = 0;
      config.value.loopUnit = 'h';

      expect(partsOf(generateFromBuilder())).toContain('loop=15m');
    });

    it('opens with the agent path and closes with the output file', () => {
      const { config, optionStates, generateFromBuilder } = useProfilerConfig();
      config.value.agentPathCustom = AGENT;
      optionStates.value.alloc = true;

      const parts = partsOf(generateFromBuilder());
      expect(parts[0]).toBe(`-agentpath:${AGENT}=start`);
      expect(parts[parts.length - 1]).toBe(`file=${DEFAULT_OUTPUT_FILE}`);
    });
  });

  describe('builderTokens', () => {
    /**
     * The chips and the command are two independent implementations of the same
     * assembly and they order the options differently, so they are compared as sets.
     */
    it('describes the same option set as the generated command', () => {
      const { config, optionStates, builderTokens, generateFromBuilder } = useProfilerConfig();
      config.value.agentPathCustom = AGENT;
      optionStates.value.alloc = true;
      config.value.allocThresholdEnabled = true;
      config.value.allocValue = 512;
      config.value.allocUnit = 'kb';
      optionStates.value.lock = true;
      config.value.lockThresholdValue = 10;
      optionStates.value.event = true;
      config.value.intervalValue = 10;
      optionStates.value.wall = true;
      config.value.wallValue = 50;
      optionStates.value.methodTracing = true;
      config.value.methodTraces = [
        { pattern: 'java.net.Socket.*', latencyValue: 2, latencyUnit: 'ms' }
      ];
      optionStates.value.nativeMem = true;
      config.value.nativeMemValue = 4;
      optionStates.value.jfrsync = true;
      optionStates.value.chunksize = true;
      optionStates.value.chunktime = true;

      const fromChips = builderTokens.value.flatMap(token => token.value.split(','));
      expect(new Set(fromChips)).toEqual(new Set(partsOf(generateFromBuilder())));
    });

    it('reports the typed agent path rather than a placeholder', () => {
      const { config, builderTokens } = useProfilerConfig();
      config.value.agentPathCustom = AGENT;

      const agent = builderTokens.value.find(token => token.key === 'agent');
      expect(agent?.value).toBe(`-agentpath:${AGENT}=start`);
    });
  });
});
