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
import {
  anyEscaped,
  bySpan,
  descendantEntryCounts,
  offsetPercent,
  severityRank,
  worstSeverity
} from '@/services/trace/traceEntries';
import type {
  NotificationSeverity,
  TraceExceptionRow,
  TraceNotificationRow,
  TraceSpanRow
} from '@/services/api/model/trace/TraceModels';

function notification(
  spanId: string | null,
  severity: NotificationSeverity | null,
  startEpochMicros = 0
): TraceNotificationRow {
  return {
    spanId,
    notificationId: `n-${spanId}-${severity}-${startEpochMicros}`,
    startMillisFromBeginning: 0,
    startEpochMicros,
    type: 'CART_REPRICED',
    message: null,
    severity,
    category: null,
    source: null,
    attributes: null,
    threadHash: 't1'
  };
}

function thrown(spanId: string, escaped: boolean, startEpochMicros = 0): TraceExceptionRow {
  return {
    spanId,
    exceptionId: `e-${spanId}-${escaped}-${startEpochMicros}`,
    startMillisFromBeginning: 0,
    startEpochMicros,
    eventType: 'jdk.JavaExceptionThrow',
    thrownClass: 'java.io.IOException',
    message: null,
    escaped,
    stacktraceId: null,
    threadHash: 't1'
  };
}

/** Only the fields the entry helpers read; the rest of a span row is irrelevant here. */
function span(spanId: string, parentSpanId: string | null, depth: number): TraceSpanRow {
  return {
    spanId,
    parentSpanId,
    name: spanId,
    kind: 'INTERNAL',
    status: 'OK',
    errorType: null,
    startMillisFromBeginning: 0,
    startEpochMicros: 0,
    durationNanos: 0,
    selfDurationNanos: 0,
    criticalPathNanos: 0,
    depth,
    threadHash: 't1',
    threadName: 'main',
    isVirtual: false,
    eventType: 'jeffrey.TraceSpan',
    attributes: null,
    eventFields: null,
    synthesized: false
  };
}

describe('traceEntries', () => {
  describe('worstSeverity', () => {
    it('ranks CRITICAL above HIGH above MEDIUM above LOW', () => {
      expect(severityRank('CRITICAL')).toBeLessThan(severityRank('HIGH'));
      expect(severityRank('HIGH')).toBeLessThan(severityRank('MEDIUM'));
      expect(severityRank('MEDIUM')).toBeLessThan(severityRank('LOW'));
    });

    it('picks the worst regardless of the order they arrived in', () => {
      const entries = [notification('a', 'LOW'), notification('a', 'CRITICAL'), notification('a', 'MEDIUM')];

      expect(worstSeverity(entries)).toBe('CRITICAL');
      expect(worstSeverity([...entries].reverse())).toBe('CRITICAL');
    });

    it('reports nothing for an empty group, so a badge with no entries draws no colour', () => {
      expect(worstSeverity([])).toBeNull();
    });

    it('ignores a severity it cannot rank rather than letting it win', () => {
      expect(worstSeverity([notification('a', null), notification('a', 'MEDIUM')])).toBe('MEDIUM');
      expect(worstSeverity([notification('a', null)])).toBeNull();
    });
  });

  describe('anyEscaped', () => {
    it('is true when one throw escaped and false when every one was caught', () => {
      expect(anyEscaped([thrown('a', false), thrown('a', true)])).toBe(true);
      expect(anyEscaped([thrown('a', false), thrown('a', false)])).toBe(false);
      expect(anyEscaped([])).toBe(false);
    });
  });

  describe('bySpan', () => {
    it('groups entries under the span that raised them', () => {
      const grouped = bySpan([notification('a', 'LOW', 1), notification('b', 'HIGH', 2), notification('a', 'MEDIUM', 3)]);

      expect(grouped.get('a')).toHaveLength(2);
      expect(grouped.get('b')).toHaveLength(1);
    });

    it('leaves out entries with no span, since there is no row to hang them on', () => {
      const grouped = bySpan([notification(null, 'CRITICAL'), notification('a', 'LOW')]);

      expect([...grouped.keys()]).toEqual(['a']);
    });

    it('keeps the order it was given, which is the order they happened', () => {
      const grouped = bySpan([notification('a', 'LOW', 3), notification('a', 'HIGH', 1)]);

      expect(grouped.get('a')?.map(entry => entry.startEpochMicros)).toEqual([3, 1]);
    });
  });

  describe('descendantEntryCounts', () => {
    // root > child > leaf, which is the shape a fold has to report through.
    const spans = [span('root', null, 0), span('child', 'root', 1), span('leaf', 'child', 2)];

    it('counts what a fold would swallow, excluding the span itself', () => {
      const counts = descendantEntryCounts(spans, [notification('leaf', 'CRITICAL')]);

      expect(counts.get('root')).toBe(1);
      expect(counts.get('child')).toBe(1);
      // Its own entry stays pinned to its own bar, which folding never hides.
      expect(counts.get('leaf')).toBeUndefined();
    });

    it('adds up every entry in the subtree', () => {
      const counts = descendantEntryCounts(spans, [
        notification('leaf', 'LOW', 1),
        notification('child', 'HIGH', 2),
        notification('leaf', 'MEDIUM', 3)
      ]);

      expect(counts.get('root')).toBe(3);
      expect(counts.get('child')).toBe(2);
    });

    it('ignores an entry whose span is not in the trace, rather than walking into nothing', () => {
      const counts = descendantEntryCounts(spans, [notification('ghost', 'HIGH'), notification(null, 'LOW')]);

      expect(counts.size).toBe(0);
    });
  });

  describe('offsetPercent', () => {
    const window = { startMicros: 1_000, endMicros: 2_000 };

    it('places an instant across the window', () => {
      expect(offsetPercent(1_000, window)).toBe(0);
      expect(offsetPercent(1_500, window)).toBe(50);
      expect(offsetPercent(2_000, window)).toBe(100);
    });

    it('clamps, so a throw at the closing microsecond lands on the edge and not past it', () => {
      expect(offsetPercent(2_400, window)).toBe(100);
      expect(offsetPercent(600, window)).toBe(0);
    });

    it('puts everything at the start when there is no axis to place it on', () => {
      expect(offsetPercent(5_000, { startMicros: 7, endMicros: 7 })).toBe(0);
    });
  });
});
