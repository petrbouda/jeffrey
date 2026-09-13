import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ReplayStreamClient from './ReplayStreamClient';

// EventSource is a browser transport, unavailable in the Node test environment.
// Drive its incoming events while exercising the real clients and their callbacks.
class TestEventSource extends EventTarget {
  static latest: TestEventSource;
  onerror: (() => void) | null = null;
  closed = false;

  constructor(readonly url: string) {
    super();
    TestEventSource.latest = this;
  }

  close(): void {
    this.closed = true;
  }

  receive(type: string, data = ''): void {
    this.dispatchEvent(new MessageEvent(type, { data }));
  }
}

beforeEach(() => {
  vi.stubGlobal('EventSource', TestEventSource);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function startReplay(options?: { startTime?: number; endTime?: number }) {
  const client = new ReplayStreamClient('hub-1', 'workspace-1', 'project-1');
  const onEvents = vi.fn();
  const onComplete = vi.fn();
  const onError = vi.fn();
  client.replay('session-1', ['jdk.GarbageCollection'], onEvents, onComplete, onError, options);
  return { client, source: TestEventSource.latest, onEvents, onComplete, onError };
}

describe('replay streaming', () => {
  it('routes through the selected hub and preserves the session, filter and time window', () => {
    const { source } = startReplay({ startTime: 1000, endTime: 2000 });
    const url = new URL(source.url, 'http://localhost');
    expect(url.pathname).toBe(
      '/api/internal/hubs/hub-1/workspaces/workspace-1/projects/project-1/replay-stream/subscribe'
    );
    expect(Object.fromEntries(url.searchParams)).toEqual({
      sessionId: 'session-1',
      eventTypes: 'jdk.GarbageCollection',
      startTime: '1000',
      endTime: '2000'
    });
  });

  it('omits time boundaries for Beginning to Latest', () => {
    const { source } = startReplay();
    const url = new URL(source.url, 'http://localhost');
    expect(url.searchParams.has('startTime')).toBe(false);
    expect(url.searchParams.has('endTime')).toBe(false);
  });

  it('delivers events and completes only after the server completion event', () => {
    const { source, onEvents, onComplete, onError } = startReplay();
    const batch = [
      { eventType: 'jdk.GarbageCollection', sessionId: 'session-1', timestamp: 1000, fields: {} }
    ];
    source.receive('events', JSON.stringify(batch));
    expect(onEvents).toHaveBeenCalledWith(batch);
    expect(onComplete).not.toHaveBeenCalled();
    source.receive('complete');
    source.onerror?.();
    expect(onComplete).toHaveBeenCalledTimes(1);
    expect(onError).not.toHaveBeenCalled();
    expect(source.closed).toBe(true);
  });

  it('reports a failed connection instead of successful completion', () => {
    const { source, onComplete, onError } = startReplay();
    source.onerror?.();
    source.onerror?.();
    expect(onError).toHaveBeenCalledExactlyOnceWith('Replay connection lost before completion');
    expect(onComplete).not.toHaveBeenCalled();
    expect(source.closed).toBe(true);
  });

  it('reports an interrupted replay even after receiving a batch', () => {
    const { source, onComplete, onError } = startReplay();
    source.receive('events', '[]');
    source.onerror?.();
    expect(onError).toHaveBeenCalledTimes(1);
    expect(onComplete).not.toHaveBeenCalled();
  });

  it('cancels replay by closing the connection without reporting a result', () => {
    const { client, source, onComplete, onError } = startReplay();
    client.cancel();
    client.cancel();

    expect(source.closed).toBe(true);
    expect(onComplete).not.toHaveBeenCalled();
    expect(onError).not.toHaveBeenCalled();
  });

  it('closes the previous stream before starting a different replay', () => {
    const { client, source } = startReplay();
    client.replay('session-2', [], vi.fn(), vi.fn(), vi.fn());

    expect(source.closed).toBe(true);
    expect(TestEventSource.latest.closed).toBe(false);
    const url = new URL(TestEventSource.latest.url, 'http://localhost');
    expect(url.searchParams.get('sessionId')).toBe('session-2');
    expect(url.searchParams.has('eventTypes')).toBe(false);
  });

  it('preserves server errors without reporting a second failure on disconnect', () => {
    const { source, onComplete, onError } = startReplay();
    source.receive('replayError', 'Session not found');
    source.onerror?.();
    expect(onError).toHaveBeenCalledExactlyOnceWith('Session not found');
    expect(onComplete).not.toHaveBeenCalled();
    expect(source.closed).toBe(true);
  });
});
