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
  contextColor,
  CUSTOM_SPAN_FAMILY,
  errorLabel,
  isIoCategory,
  isMethodEventType,
  METHOD_TRACE_EVENT_TYPE,
  operationKey,
  parseOperationName,
  promotedCategory,
  SPAN_FAMILIES,
  spanEventColor,
  spanFamiliesOf,
  spanFamily,
  spanFamilyColor,
  spanKindVariant
} from '@/services/trace/traceLabels';
import type { TraceOperationId } from '@/services/api/model/trace/TraceModels';

describe('errorLabel', () => {
  it('says "1 error", not "1 errors"', () => {
    expect(errorLabel(1)).toBe('1 error');
  });

  it('pluralises everything else', () => {
    expect(errorLabel(0)).toBe('0 errors');
    expect(errorLabel(3)).toBe('3 errors');
  });
});

describe('isIoCategory', () => {
  it('puts both I/O families on the I/O side of the split', () => {
    expect(isIoCategory('SOCKET_IO')).toBe(true);
    expect(isIoCategory('FILE_IO')).toBe(true);
  });

  it('leaves the blocking waits on the other side', () => {
    expect(isIoCategory('MONITOR_BLOCKED')).toBe(false);
    expect(isIoCategory('PARKED')).toBe(false);
    expect(isIoCategory('SLEEPING')).toBe(false);
    expect(isIoCategory('VT_PINNED')).toBe(false);
  });

  it('splits every promoted event type into exactly one of the two masters', () => {
    // The waterfall's two toolbar masters partition the promoted set between them; a promoted
    // event type whose category answered neither master would be a row no toggle governs.
    const promotedEventTypes = [
      'jdk.SocketRead',
      'jdk.SocketWrite',
      'jdk.FileRead',
      'jdk.FileWrite',
      'jdk.FileForce',
      'jdk.JavaMonitorEnter',
      'jdk.JavaMonitorWait',
      'jdk.ThreadPark',
      'jdk.ThreadSleep',
      'jdk.ZAllocationStall',
      'jdk.VirtualThreadPinned'
    ];

    const io = promotedEventTypes.filter(eventType => {
      const category = promotedCategory(eventType);
      return category !== null && isIoCategory(category);
    });

    expect(io).toEqual([
      'jdk.SocketRead',
      'jdk.SocketWrite',
      'jdk.FileRead',
      'jdk.FileWrite',
      'jdk.FileForce'
    ]);
  });
});

describe('spanKindVariant', () => {
  it('gives each kind its own variant, so the three never read alike', () => {
    const variants = [
      spanKindVariant('SERVER'),
      spanKindVariant('CLIENT'),
      spanKindVariant('INTERNAL')
    ];

    expect(new Set(variants).size).toBe(3);
  });
});

describe('the span palette', () => {
  const KNOWN_SPAN_EVENT_TYPES = [
    'jeffrey.HttpServerExchange',
    'jeffrey.HttpClientExchange',
    'jeffrey.GrpcServerExchange',
    'jeffrey.GrpcClientExchange',
    'jeffrey.JdbcQuery',
    'jeffrey.JdbcInsert',
    'jeffrey.JdbcUpdate',
    'jeffrey.JdbcDelete',
    'jeffrey.JdbcExecute',
    'jeffrey.JdbcStream',
    METHOD_TRACE_EVENT_TYPE
  ];

  it('gives every family a colour no other family wears', () => {
    // The ramp's whole job is to be read back: two families sharing a hue would make the legend
    // decode one bar into two different answers.
    const colors = Object.values(SPAN_FAMILIES).map(family => family.color);

    expect(new Set(colors).size).toBe(colors.length);
  });

  it('shares no colour with the context ramp, which is drawn in the same waterfall', () => {
    const contextColors = new Set(
      [
        'GC_PAUSE',
        'SAFEPOINT',
        'MONITOR_BLOCKED',
        'MONITOR_WAIT',
        'PARKED',
        'SLEEPING',
        'SOCKET_IO',
        'FILE_IO',
        'ALLOCATION_STALL',
        'DEOPTIMIZATION',
        'ALLOCATION_REQUIRING_GC',
        'VT_PINNED',
        'CPU_THROTTLED'
      ].map(contextColor)
    );
    const shared = Object.values(SPAN_FAMILIES)
      .map(family => family.color)
      .filter(color => contextColors.has(color));

    expect(shared).toEqual([]);
  });

  it('keeps the traced-method green the own-work green, because it is the same quantity', () => {
    expect(spanFamilyColor('METHOD')).toBe(contextColor('OWN_WORK'));
  });

  it('classifies every event type the backend holds a span convention for', () => {
    const unclassified = KNOWN_SPAN_EVENT_TYPES.filter(
      eventType => spanFamily(eventType) === CUSTOM_SPAN_FAMILY
    );

    expect(unclassified).toEqual([]);
  });

  it('drops a hand-written span and an unmet instrumentation into the same grey', () => {
    // The point of the fallback: "we wrote this ourselves" and "nobody here has met this" are the
    // same fact as far as a colour can say it, so they must not be told apart by one.
    expect(spanEventColor('jeffrey.TraceSpan')).toBe(spanFamilyColor(CUSTOM_SPAN_FAMILY));
    expect(spanEventColor('acme.SomethingNobodyHasMet')).toBe(spanFamilyColor(CUSTOM_SPAN_FAMILY));
  });

  it('lets a promoted wait keep its context colour rather than taking a family one', () => {
    expect(spanEventColor('jdk.SocketRead')).toBe(contextColor('SOCKET_IO'));
    expect(spanEventColor('jdk.ThreadPark')).toBe(contextColor('PARKED'));
  });

  it('leaves promoted waits out of the family legend, which the context entries already decode', () => {
    expect(spanFamiliesOf(['jdk.SocketRead', 'jdk.ThreadPark'])).toEqual([]);
  });

  it('lists the families a trace carries in palette order, not in arrival order', () => {
    // A legend that reshuffled itself between two traces of the same service would have to be
    // re-read each time; the order is the palette's, so it is the same order every time.
    const families = spanFamiliesOf([
      'jeffrey.TraceSpan',
      'jeffrey.JdbcQuery',
      'jdk.FileRead',
      'jeffrey.HttpServerExchange',
      'jeffrey.JdbcStream'
    ]);

    expect(families).toEqual(['HTTP_SERVER', 'DATABASE', 'CUSTOM']);
  });
});

describe('operationKey', () => {
  const inbound: TraceOperationId = {
    name: 'GET /api/internal/health',
    kind: 'SERVER',
    eventType: 'jeffrey.HttpServerExchange'
  };

  it('separates an inbound call from an outbound one of the same name', () => {
    // The whole reason the key is not the name: these are two operations, and a list keyed on the
    // name alone would collide them into one row.
    const outbound: TraceOperationId = {
      ...inbound,
      kind: 'CLIENT',
      eventType: 'jeffrey.HttpClientExchange'
    };

    expect(operationKey(inbound)).not.toBe(operationKey(outbound));
  });

  it('is stable for the same identity', () => {
    expect(operationKey(inbound)).toBe(operationKey({ ...inbound }));
  });

  it('separates operations differing only in kind', () => {
    expect(operationKey(inbound)).not.toBe(operationKey({ ...inbound, kind: 'INTERNAL' }));
  });

  it('separates operations differing only in instrumentation', () => {
    expect(operationKey(inbound)).not.toBe(
      operationKey({ ...inbound, eventType: 'jeffrey.TraceSpan' })
    );
  });
});

describe('parseOperationName', () => {
  it('highlights the method of an HTTP operation and styles the URI like an endpoint', () => {
    const segments = parseOperationName(
      'POST /api/recordings/{recordingId}/analyze',
      'jeffrey.HttpServerExchange'
    );

    expect(segments[0]).toEqual({ kind: 'group', text: 'POST' });
    expect(segments).toContainEqual({ kind: 'var', text: '{recordingId}' });
    expect(segments).toContainEqual({ kind: 'segment', text: 'analyze' });
    expect(segments.map(s => s.text).join('')).toBe('POST /api/recordings/{recordingId}/analyze');
  });

  it('leaves an HTTP-typed name that is not METHOD /uri shaped alone', () => {
    const segments = parseOperationName('something odd', 'jeffrey.HttpServerExchange');

    expect(segments).toEqual([{ kind: 'name', text: 'something odd' }]);
  });

  it('splits a gRPC operation into package, service and method', () => {
    const segments = parseOperationName(
      'jeffrey.api.v1.ProjectService/List',
      'jeffrey.GrpcServerExchange'
    );

    expect(segments).toContainEqual({ kind: 'path', text: 'jeffrey.api.v1.' });
    expect(segments).toContainEqual({ kind: 'leaf', text: 'ProjectService' });
    expect(segments).toContainEqual({ kind: 'leaf', text: 'List' });
    expect(segments.map(s => s.text).join('')).toBe('jeffrey.api.v1.ProjectService/List');
  });

  it('falls back to the grouped span-tag parse for other event types', () => {
    expect(parseOperationName('heap-dump-init', 'jeffrey.TraceSpan')).toEqual([
      { kind: 'name', text: 'heap-dump-init' }
    ]);
    expect(parseOperationName('profile.initialize', 'jeffrey.TraceSpan')[0]).toEqual({
      kind: 'group',
      text: 'profile'
    });
  });

  it('renders the fallback for an empty name', () => {
    expect(parseOperationName('', 'jeffrey.TraceSpan')).toEqual([
      { kind: 'name', text: '(unnamed)' }
    ]);
  });
});

describe('isMethodEventType', () => {
  it('recognises a promoted traced method', () => {
    expect(isMethodEventType(METHOD_TRACE_EVENT_TYPE)).toBe(true);
  });

  it('does not treat a promoted wait as one', () => {
    expect(isMethodEventType('jdk.SocketRead')).toBe(false);
    expect(isMethodEventType('jeffrey.TraceSpan')).toBe(false);
  });

  it('maps to no context category, so its time stays own work', () => {
    // The why-slow panel rebuilds a promoted category's total from the synthesized spans carrying
    // it. A traced method is the trace's own work, not a wait, so giving it a category here would
    // move its time out of OWN_WORK and into a wait total that never happened.
    expect(promotedCategory(METHOD_TRACE_EVENT_TYPE)).toBeNull();
  });
});
