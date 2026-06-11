import { beforeEach, describe, expect, it, vi } from 'vitest';
import axios from 'axios';
import HttpUtils from '@/services/HttpUtils';
import TimeRange from '@/services/api/model/TimeRange';
import GraphComponents from '@/services/api/model/GraphComponents';
import PrimaryFlamegraphClient from '@/services/api/PrimaryFlamegraphClient';
import SingleSpanFlamegraphClient from '@/services/api/SingleSpanFlamegraphClient';
import SpanFlamegraphClient from '@/services/api/SpanFlamegraphClient';
import DifferentialFlamegraphClient from '@/services/api/DifferentialFlamegraphClient';
import GuardianFlamegraphClient from '@/services/api/GuardianFlamegraphClient';

vi.mock('axios', () => ({
  default: {
    post: vi.fn()
  }
}));

const fakeFlamegraph = { depth: 3 };
const fakeTimeseries = { series: [] };

vi.mock('@/services/flamegraphs/ProtobufConverter', () => ({
  default: {
    decode: vi.fn(() => ({ flamegraph: { depth: 3 }, timeseries: { series: [] } }))
  }
}));

/**
 * These tests pin the exact serialized request payloads of every flamegraph client operation.
 * The expected objects are transcribed FROM THE ORIGINAL HAND-BUILT IMPLEMENTATIONS (before the
 * consolidation into RemoteFlamegraphClient) — comparing JSON.stringify output asserts key
 * presence, values AND key order, i.e. byte-identical payloads on the wire.
 */
const timeRange = new TimeRange(1000, 2000, true);

function postMock() {
  return vi.mocked(axios.post);
}

function lastPost(): { url: string; bodyJson: string; config: unknown } {
  const calls = postMock().mock.calls;
  const [url, body, config] = calls[calls.length - 1];
  return { url: url as string, bodyJson: JSON.stringify(body), config };
}

function expectJson(expected: Record<string, unknown>): string {
  return JSON.stringify(expected);
}

beforeEach(() => {
  postMock().mockReset();
  postMock().mockResolvedValue({ data: new ArrayBuffer(0) });
});

describe('PrimaryFlamegraphClient payloads', () => {
  function client(): PrimaryFlamegraphClient {
    return new PrimaryFlamegraphClient(
      'p1',
      'jdk.ExecutionSample',
      true,
      false,
      true,
      false,
      true,
      null
    );
  }

  it('provideBoth sends the full content with timeRange and search', async () => {
    const result = await client().provideBoth(GraphComponents.BOTH, timeRange, 'lambda');

    const { url, bodyJson, config } = lastPost();
    expect(url).toBe('/api/internal/profiles/p1/flamegraph');
    expect(config).toBe(HttpUtils.PROTOBUF_HEADERS);
    expect(bodyJson).toBe(
      expectJson({
        eventType: 'jdk.ExecutionSample',
        useWeight: false,
        useThreadMode: true,
        timeRange: { start: 1000, end: 2000, absoluteTime: true },
        search: 'lambda',
        excludeNonJavaSamples: true,
        excludeIdleSamples: false,
        onlyUnsafeAllocationSamples: true,
        threadInfo: null,
        components: 'BOTH'
      })
    );
    expect(result.flamegraph).toEqual(fakeFlamegraph);
    expect(result.timeseries).toEqual(fakeTimeseries);
  });

  it('provideBoth keeps null timeRange and search keys present', async () => {
    await client().provideBoth(GraphComponents.BOTH, null, null);

    expect(lastPost().bodyJson).toBe(
      expectJson({
        eventType: 'jdk.ExecutionSample',
        useWeight: false,
        useThreadMode: true,
        timeRange: null,
        search: null,
        excludeNonJavaSamples: true,
        excludeIdleSamples: false,
        onlyUnsafeAllocationSamples: true,
        threadInfo: null,
        components: 'BOTH'
      })
    );
  });

  it('provide omits the search key entirely', async () => {
    const result = await client().provide(timeRange);

    expect(lastPost().bodyJson).toBe(
      expectJson({
        eventType: 'jdk.ExecutionSample',
        useWeight: false,
        useThreadMode: true,
        timeRange: { start: 1000, end: 2000, absoluteTime: true },
        excludeNonJavaSamples: true,
        excludeIdleSamples: false,
        onlyUnsafeAllocationSamples: true,
        threadInfo: null,
        components: 'FLAMEGRAPH_ONLY'
      })
    );
    expect(result).toEqual(fakeFlamegraph);
  });

  it('provideTimeseries omits the timeRange key entirely', async () => {
    const result = await client().provideTimeseries('alloc');

    expect(lastPost().bodyJson).toBe(
      expectJson({
        eventType: 'jdk.ExecutionSample',
        useWeight: false,
        useThreadMode: true,
        search: 'alloc',
        excludeNonJavaSamples: true,
        excludeIdleSamples: false,
        onlyUnsafeAllocationSamples: true,
        threadInfo: null,
        components: 'TIMESERIES_ONLY'
      })
    );
    expect(result).toEqual(fakeTimeseries);
  });

  it('save posts to the repository without useWeight and threadInfo keys', async () => {
    await client().save(GraphComponents.BOTH, 'my-flamegraph', timeRange);

    const { url, bodyJson, config } = lastPost();
    expect(url).toBe('/api/internal/profiles/p1/flamegraph/repository');
    expect(config).toBe(HttpUtils.JSON_HEADERS);
    expect(bodyJson).toBe(
      expectJson({
        flamegraphName: 'my-flamegraph',
        eventType: 'jdk.ExecutionSample',
        timeRange: { start: 1000, end: 2000, absoluteTime: true },
        useThreadMode: true,
        excludeNonJavaSamples: true,
        excludeIdleSamples: false,
        onlyUnsafeAllocationSamples: true,
        components: 'BOTH'
      })
    );
  });

  it('mode toggles are reflected in subsequent payloads', async () => {
    const toggled = client();
    expect(toggled.supportsModeToggle()).toBe(true);
    toggled.setUseThreadMode(false);
    toggled.setUseWeight(true);

    await toggled.provide(null);

    expect(lastPost().bodyJson).toBe(
      expectJson({
        eventType: 'jdk.ExecutionSample',
        useWeight: true,
        useThreadMode: false,
        timeRange: null,
        excludeNonJavaSamples: true,
        excludeIdleSamples: false,
        onlyUnsafeAllocationSamples: true,
        threadInfo: null,
        components: 'FLAMEGRAPH_ONLY'
      })
    );
  });
});

describe('SingleSpanFlamegraphClient payloads', () => {
  function client(): SingleSpanFlamegraphClient {
    return new SingleSpanFlamegraphClient(
      'p1',
      '12345678901234567890',
      5000,
      9000,
      'jdk.ExecutionSample',
      false,
      true,
      false,
      true,
      false
    );
  }

  const expectedBody = (components: string) =>
    expectJson({
      threadHash: '12345678901234567890',
      fromMillis: 5000,
      toMillis: 9000,
      eventType: 'jdk.ExecutionSample',
      useWeight: true,
      useThreadMode: false,
      excludeNonJavaSamples: false,
      excludeIdleSamples: true,
      onlyUnsafeAllocationSamples: false,
      components: components
    });

  it('provideBoth ignores timeRange and search', async () => {
    await client().provideBoth(GraphComponents.BOTH, timeRange, 'ignored');

    const { url, bodyJson, config } = lastPost();
    expect(url).toBe('/api/internal/profiles/p1/async-profiler/spans/single/flamegraph');
    expect(config).toBe(HttpUtils.PROTOBUF_HEADERS);
    expect(bodyJson).toBe(expectedBody('BOTH'));
  });

  it('provide sends the span-scoped content', async () => {
    await client().provide(timeRange);

    expect(lastPost().bodyJson).toBe(expectedBody('FLAMEGRAPH_ONLY'));
  });

  it('provideTimeseries sends the span-scoped content', async () => {
    await client().provideTimeseries('ignored');

    expect(lastPost().bodyJson).toBe(expectedBody('TIMESERIES_ONLY'));
  });

  it('save is rejected', async () => {
    await expect(client().save()).rejects.toThrow(
      'Saving span-scoped flamegraphs is not supported'
    );
    expect(postMock()).not.toHaveBeenCalled();
  });
});

describe('SpanFlamegraphClient payloads', () => {
  function client(): SpanFlamegraphClient {
    return new SpanFlamegraphClient(
      'p1',
      'span-tag-1',
      'jdk.ExecutionSample',
      true,
      null,
      true,
      false,
      true
    );
  }

  const expectedBody = (components: string) =>
    expectJson({
      tag: 'span-tag-1',
      eventType: 'jdk.ExecutionSample',
      useWeight: null,
      useThreadMode: true,
      excludeNonJavaSamples: true,
      excludeIdleSamples: false,
      onlyUnsafeAllocationSamples: true,
      components: components
    });

  it('provideBoth ignores timeRange and search', async () => {
    await client().provideBoth(GraphComponents.BOTH, timeRange, 'ignored');

    const { url, bodyJson, config } = lastPost();
    expect(url).toBe('/api/internal/profiles/p1/async-profiler/spans/flamegraph');
    expect(config).toBe(HttpUtils.PROTOBUF_HEADERS);
    expect(bodyJson).toBe(expectedBody('BOTH'));
  });

  it('provide sends the tag-scoped content', async () => {
    await client().provide(null);

    expect(lastPost().bodyJson).toBe(expectedBody('FLAMEGRAPH_ONLY'));
  });

  it('provideTimeseries sends the tag-scoped content', async () => {
    await client().provideTimeseries(null);

    expect(lastPost().bodyJson).toBe(expectedBody('TIMESERIES_ONLY'));
  });

  it('save is rejected', async () => {
    await expect(client().save()).rejects.toThrow(
      'Saving span-scoped flamegraphs is not supported'
    );
    expect(postMock()).not.toHaveBeenCalled();
  });
});

describe('DifferentialFlamegraphClient payloads', () => {
  function client(): DifferentialFlamegraphClient {
    return new DifferentialFlamegraphClient(
      'primary-1',
      'secondary-2',
      'jdk.ObjectAllocationSample',
      true,
      false,
      true,
      false
    );
  }

  it('provideBoth sends content without thread-related keys', async () => {
    await client().provideBoth(GraphComponents.BOTH, timeRange, 'alloc');

    const { url, bodyJson, config } = lastPost();
    expect(url).toBe(
      '/api/internal/profiles/primary-1/diff/secondary-2/differential-flamegraph'
    );
    expect(config).toBe(HttpUtils.PROTOBUF_HEADERS);
    expect(bodyJson).toBe(
      expectJson({
        eventType: 'jdk.ObjectAllocationSample',
        useWeight: true,
        timeRange: { start: 1000, end: 2000, absoluteTime: true },
        search: 'alloc',
        excludeNonJavaSamples: false,
        excludeIdleSamples: true,
        onlyUnsafeAllocationSamples: false,
        components: 'BOTH'
      })
    );
  });

  it('provide omits the search key entirely', async () => {
    await client().provide(timeRange);

    expect(lastPost().bodyJson).toBe(
      expectJson({
        eventType: 'jdk.ObjectAllocationSample',
        useWeight: true,
        timeRange: { start: 1000, end: 2000, absoluteTime: true },
        search: undefined,
        excludeNonJavaSamples: false,
        excludeIdleSamples: true,
        onlyUnsafeAllocationSamples: false,
        components: 'FLAMEGRAPH_ONLY'
      })
    );
  });

  it('provideTimeseries delegates with explicit null timeRange and search keys', async () => {
    const result = await client().provideTimeseries('ignored-search');

    expect(lastPost().bodyJson).toBe(
      expectJson({
        eventType: 'jdk.ObjectAllocationSample',
        useWeight: true,
        timeRange: null,
        search: null,
        excludeNonJavaSamples: false,
        excludeIdleSamples: true,
        onlyUnsafeAllocationSamples: false,
        components: 'TIMESERIES_ONLY'
      })
    );
    expect(result).toEqual(fakeTimeseries);
  });

  it('save posts to the repository without useWeight and useThreadMode keys', async () => {
    await client().save(GraphComponents.BOTH, 'diff-flamegraph', timeRange);

    const { url, bodyJson, config } = lastPost();
    expect(url).toBe(
      '/api/internal/profiles/primary-1/diff/secondary-2/differential-flamegraph/repository'
    );
    expect(config).toBe(HttpUtils.JSON_HEADERS);
    expect(bodyJson).toBe(
      expectJson({
        flamegraphName: 'diff-flamegraph',
        eventType: 'jdk.ObjectAllocationSample',
        timeRange: { start: 1000, end: 2000, absoluteTime: true },
        excludeNonJavaSamples: false,
        excludeIdleSamples: true,
        onlyUnsafeAllocationSamples: false,
        components: 'BOTH'
      })
    );
  });
});

describe('GuardianFlamegraphClient payloads', () => {
  const markers = [{ type: 'WARNING', from: 1, to: 2 }];

  function client(): GuardianFlamegraphClient {
    return new GuardianFlamegraphClient('p1', 'jdk.ExecutionSample', true, markers);
  }

  it('provideBoth sends hardcoded thread/exclude fields with markers', async () => {
    await client().provideBoth(GraphComponents.BOTH, timeRange, 'guard');

    const { url, bodyJson, config } = lastPost();
    expect(url).toBe('/api/internal/profiles/p1/flamegraph');
    expect(config).toBe(HttpUtils.PROTOBUF_HEADERS);
    expect(bodyJson).toBe(
      expectJson({
        eventType: 'jdk.ExecutionSample',
        useWeight: true,
        markers: markers,
        useThreadMode: false,
        timeRange: { start: 1000, end: 2000, absoluteTime: true },
        search: 'guard',
        excludeNonJavaSamples: false,
        excludeIdleSamples: false,
        onlyUnsafeAllocationSamples: false,
        threadInfo: null,
        components: 'BOTH'
      })
    );
  });

  it('provide places timeRange before useThreadMode (historical order)', async () => {
    await client().provide(timeRange);

    expect(lastPost().bodyJson).toBe(
      expectJson({
        eventType: 'jdk.ExecutionSample',
        useWeight: true,
        markers: markers,
        timeRange: { start: 1000, end: 2000, absoluteTime: true },
        useThreadMode: false,
        excludeNonJavaSamples: false,
        excludeIdleSamples: false,
        onlyUnsafeAllocationSamples: false,
        threadInfo: null,
        components: 'FLAMEGRAPH_ONLY'
      })
    );
  });

  it('provideTimeseries places search before useThreadMode (historical order)', async () => {
    await client().provideTimeseries('guard');

    expect(lastPost().bodyJson).toBe(
      expectJson({
        eventType: 'jdk.ExecutionSample',
        useWeight: true,
        markers: markers,
        search: 'guard',
        useThreadMode: false,
        excludeNonJavaSamples: false,
        excludeIdleSamples: false,
        onlyUnsafeAllocationSamples: false,
        threadInfo: null,
        components: 'TIMESERIES_ONLY'
      })
    );
  });

  it('save posts useWeight and markers but no exclude/thread keys', async () => {
    await client().save(GraphComponents.BOTH, 'guardian-flamegraph', timeRange);

    const { url, bodyJson, config } = lastPost();
    expect(url).toBe('/api/internal/profiles/p1/flamegraph/repository');
    expect(config).toBe(HttpUtils.JSON_HEADERS);
    expect(bodyJson).toBe(
      expectJson({
        flamegraphName: 'guardian-flamegraph',
        eventType: 'jdk.ExecutionSample',
        timeRange: { start: 1000, end: 2000, absoluteTime: true },
        useWeight: true,
        markers: markers,
        components: 'BOTH'
      })
    );
  });
});
