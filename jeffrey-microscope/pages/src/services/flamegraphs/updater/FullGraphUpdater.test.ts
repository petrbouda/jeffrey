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

import { describe, expect, it } from 'vitest';
import FullGraphUpdater from './FullGraphUpdater';
import FlamegraphClient from '@/services/api/FlamegraphClient';
import FlamegraphData from '@/services/api/model/FlamegraphData';
import Frame from '@/services/api/model/Frame';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import Serie from '@/services/timeseries/model/Serie';
import TimeRange from '@/services/api/model/TimeRange';
import BothGraphData from '@/services/api/model/BothGraphData';
import type { TimeseriesCallbacks } from './GraphUpdater';
import type { LinkedGraphState } from '@/services/flamegraphs/FlamegraphLinkQuery';

/** Three hours of one-second buckets, so the opening zoom of 60 minutes is a real zoom. */
const THREE_HOURS_SECONDS = 3 * 60 * 60;
const TIMESERIES = new TimeseriesData([
  new Serie(
    [
      [0, 1],
      [THREE_HOURS_SECONDS, 1]
    ],
    'samples'
  )
]);
const FLAMEGRAPH = graph('root');
const LINKED_RANGE = new TimeRange(10_000, 20_000, false);
const SEARCH = 'OrderService';

/** A graph of nothing but its root frame, told apart by the root's title. */
function graph(rootTitle: string): FlamegraphData {
  return FlamegraphData.fromFrames([[new Frame(0, 1, rootTitle, 'UNKNOWN')]]);
}

/** Records what the updater asked for, in order. */
class RecordingClient extends FlamegraphClient {
  readonly calls: string[] = [];

  provideBoth(): Promise<BothGraphData> {
    throw new Error('not used by FullGraphUpdater');
  }

  provide(timeRange: TimeRange | null): Promise<FlamegraphData> {
    this.calls.push(`flamegraph ${JSON.stringify(timeRange)}`);
    return Promise.resolve(FLAMEGRAPH);
  }

  provideTimeseries(search: string | null): Promise<TimeseriesData> {
    this.calls.push(`timeseries ${search}`);
    return Promise.resolve(TIMESERIES);
  }

  save(): Promise<void> {
    return Promise.resolve();
  }

  override supportsModeToggle(): boolean {
    return true;
  }
}

interface Harness {
  client: RecordingClient;
  updater: FullGraphUpdater;
  searched: string[];
}

function harness(timeseries: boolean): Harness {
  const client = new RecordingClient();
  const updater = new FullGraphUpdater(client, true);
  const searched: string[] = [];
  updater.setTimeseriesEnabled(timeseries);
  updater.setTimeseriesSearchEnabled(timeseries);
  updater.setInitialVisibleMinutes(60);
  return { client, updater, searched };
}

const none = () => {};

/** A chart that registers and does nothing with what it is handed. */
const IDLE_TIMESERIES: TimeseriesCallbacks = {
  onUpdateStarted: none,
  onUpdateFinished: none,
  onInit: none,
  onSearch: none,
  onResetSearch: none,
  onZoom: none,
  onResetZoom: none
};

function register({ updater, searched }: Harness, timeseries: boolean): void {
  updater.registerFlamegraphCallbacks({
    onUpdateStarted: none,
    onUpdateFinished: none,
    onInit: none,
    onSearch: expression => searched.push(expression),
    onResetSearch: none,
    onZoom: none,
    onResetZoom: none
  });
  if (timeseries) {
    updater.registerTimeseriesCallbacks(IDLE_TIMESERIES);
  }
}

async function settled(): Promise<void> {
  for (let i = 0; i < 5; i++) {
    await new Promise(resolve => setTimeout(resolve, 0));
  }
}

describe('FullGraphUpdater opened from a link', () => {
  it('opens on the linked window instead of the opening zoom', async () => {
    const graph = harness(true);
    graph.updater.openAt({ timeRange: LINKED_RANGE, search: null });

    register(graph, true);
    await settled();

    expect(graph.client.calls).toEqual([
      'timeseries null',
      `flamegraph ${JSON.stringify(LINKED_RANGE)}`
    ]);
  });

  it('keeps the opening zoom of today without a linked window', async () => {
    const graph = harness(true);

    register(graph, true);
    await settled();

    expect(graph.client.calls).toEqual([
      'timeseries null',
      `flamegraph ${JSON.stringify(new TimeRange(0, 3_600_000, false))}`
    ]);
    expect(graph.searched).toEqual([]);
  });

  it('applies the linked search once the graph is drawn, as the search box does', async () => {
    const graph = harness(true);
    graph.updater.openAt({ timeRange: null, search: SEARCH });

    register(graph, true);
    await settled();

    expect(graph.client.calls.slice(-1)).toEqual([`timeseries ${SEARCH}`]);
    expect(graph.searched).toEqual([SEARCH]);
  });

  it('applies the linked search only on the first drawing, not on a later mode toggle', async () => {
    const graph = harness(true);
    graph.updater.openAt({ timeRange: null, search: SEARCH });
    register(graph, true);
    await settled();

    graph.updater.updateModes(true, false);
    await settled();

    expect(graph.searched).toEqual([SEARCH]);
  });

  it('draws the linked window and search without a timeseries', async () => {
    const graph = harness(false);
    graph.updater.openAt({ timeRange: LINKED_RANGE, search: SEARCH });

    register(graph, false);
    await settled();

    expect(graph.client.calls).toEqual([`flamegraph ${JSON.stringify(LINKED_RANGE)}`]);
    expect(graph.searched).toEqual([SEARCH]);
  });

  it('hands the linked state to the search box and the chart', () => {
    const graph = harness(true);
    graph.updater.openAt({ timeRange: LINKED_RANGE, search: SEARCH });

    expect(graph.updater.linkedSearch()).toBe(SEARCH);
    expect(graph.updater.linkedTimeRange()).toBe(LINKED_RANGE);
  });

  it('has no linked state unless a link opened it', () => {
    const graph = harness(true);

    expect(graph.updater.linkedSearch()).toBeNull();
    expect(graph.updater.linkedTimeRange()).toBeNull();
  });
});

/** A request the test answers by hand, in whatever order it likes. */
interface Pending<T> {
  resolve: (value: T) => void;
  reject: (error: Error) => void;
  signal: AbortSignal | undefined;
  /** The time range of a flamegraph request, the search of a timeseries one. */
  asked: unknown;
}

/** Holds every request open until the test answers it. */
class DeferredClient extends FlamegraphClient {
  readonly flamegraphs: Pending<FlamegraphData>[] = [];
  readonly timeseries: Pending<TimeseriesData>[] = [];

  provideBoth(): Promise<BothGraphData> {
    throw new Error('not used by FullGraphUpdater');
  }

  provide(timeRange: TimeRange | null, signal?: AbortSignal): Promise<FlamegraphData> {
    return new Promise((resolve, reject) =>
      this.flamegraphs.push({ resolve, reject, signal, asked: timeRange })
    );
  }

  provideTimeseries(search: string | null, signal?: AbortSignal): Promise<TimeseriesData> {
    return new Promise((resolve, reject) =>
      this.timeseries.push({ resolve, reject, signal, asked: search })
    );
  }

  save(): Promise<void> {
    return Promise.resolve();
  }
}

/** What reached the flamegraph panel, and whether it is still loading. */
interface Panel {
  client: DeferredClient;
  updater: FullGraphUpdater;
  drawn: FlamegraphData[];
  searched: string[];
  loading: () => boolean;
}

/** An updater whose opening requests have already been answered. */
async function openedPanel(): Promise<Panel> {
  const client = new DeferredClient();
  const updater = new FullGraphUpdater(client, true);
  const drawn: FlamegraphData[] = [];
  const searched: string[] = [];
  let loading = false;
  const draw = (data: FlamegraphData) => drawn.push(data);
  updater.registerFlamegraphCallbacks({
    onUpdateStarted: () => (loading = true),
    onUpdateFinished: () => (loading = false),
    onInit: draw,
    onSearch: expression => searched.push(expression),
    onResetSearch: none,
    onZoom: draw,
    onResetZoom: draw
  });
  updater.registerTimeseriesCallbacks(IDLE_TIMESERIES);

  client.timeseries[0].resolve(TIMESERIES);
  await settled();
  client.flamegraphs[0].resolve(FLAMEGRAPH);
  await settled();
  drawn.length = 0;
  return { client, updater, drawn, searched, loading: () => loading };
}

describe('FullGraphUpdater with answers arriving out of order', () => {
  it('draws the zoom asked for last when an earlier one answers after it', async () => {
    const panel = await openedPanel();
    const first = graph('first zoom');
    const second = graph('second zoom');

    panel.updater.updateWithZoom(new TimeRange(0, 1_000, false));
    panel.updater.updateWithZoom(new TimeRange(0, 2_000, false));
    panel.client.flamegraphs[2].resolve(second);
    await settled();
    panel.client.flamegraphs[1].resolve(first);
    await settled();

    expect(panel.drawn).toEqual([second]);
    expect(panel.loading()).toBe(false);
  });

  it('keeps loading while the zoom that overtook a failed one is still in flight', async () => {
    const panel = await openedPanel();

    panel.updater.updateWithZoom(new TimeRange(0, 1_000, false));
    panel.updater.resetZoom();
    panel.client.flamegraphs[1].reject(new Error('overtaken and failed'));
    await settled();

    expect(panel.loading()).toBe(true);

    panel.client.flamegraphs[2].resolve(FLAMEGRAPH);
    await settled();

    expect(panel.drawn).toEqual([FLAMEGRAPH]);
    expect(panel.loading()).toBe(false);
  });

  it('does not bring a search back that was reset while in flight', async () => {
    const panel = await openedPanel();

    panel.updater.updateWithSearch(SEARCH);
    expect(panel.loading()).toBe(true);
    panel.updater.resetSearch();
    expect(panel.loading()).toBe(false);

    panel.client.timeseries[1].resolve(TIMESERIES);
    await settled();

    expect(panel.searched).toEqual([]);
  });

  it('aborts the zoom the next one overtook, and only that one', async () => {
    const panel = await openedPanel();

    panel.updater.updateWithZoom(new TimeRange(0, 1_000, false));
    panel.updater.updateWithZoom(new TimeRange(0, 2_000, false));

    expect(panel.client.flamegraphs[1].signal?.aborted).toBe(true);
    expect(panel.client.flamegraphs[2].signal?.aborted).toBe(false);
  });

  it('aborts a search that was reset while in flight', async () => {
    const panel = await openedPanel();

    panel.updater.updateWithSearch(SEARCH);
    panel.updater.resetSearch();

    expect(panel.client.timeseries[1].signal?.aborted).toBe(true);
  });

  it('opens the chart although a zoom overtook the opening flamegraph', async () => {
    const client = new DeferredClient();
    const updater = new FullGraphUpdater(client, true);
    const opened: TimeseriesData[] = [];
    updater.registerFlamegraphCallbacks({
      onUpdateStarted: none,
      onUpdateFinished: none,
      onInit: none,
      onSearch: none,
      onResetSearch: none,
      onZoom: none,
      onResetZoom: none
    });
    updater.registerTimeseriesCallbacks({ ...IDLE_TIMESERIES, onInit: data => opened.push(data) });

    updater.resetZoom();
    client.timeseries[0].resolve(TIMESERIES);
    await settled();

    expect(opened).toEqual([TIMESERIES]);
    expect(client.timeseries[0].signal?.aborted).toBe(false);
  });

  it('applies only the search typed last', async () => {
    const panel = await openedPanel();

    panel.updater.updateWithSearch('Order');
    panel.updater.updateWithSearch(SEARCH);
    panel.client.timeseries[2].resolve(TIMESERIES);
    await settled();
    panel.client.timeseries[1].resolve(TIMESERIES);
    await settled();

    expect(panel.searched).toEqual([SEARCH]);
    expect(panel.loading()).toBe(false);
  });
});

describe('FullGraphUpdater opening on a window known up front', () => {
  const ONE_HOUR_MILLIS = 60 * 60 * 1000;

  /** An updater on the usual 60-minute opening window; registering it starts the opening. */
  function opening(recordingDurationMillis: number | null, linked: LinkedGraphState | null) {
    const client = new DeferredClient();
    const updater = new FullGraphUpdater(client, true);
    const events: string[] = [];
    updater.setInitialVisibleMinutes(60);
    updater.setRecordingDuration(recordingDurationMillis);
    if (linked !== null) {
      updater.openAt(linked);
    }
    updater.registerFlamegraphCallbacks({
      onUpdateStarted: none,
      onUpdateFinished: none,
      onInit: () => events.push('flamegraph drawn'),
      onSearch: expression => events.push(`search ${expression}`),
      onResetSearch: none,
      onZoom: none,
      onResetZoom: none
    });
    updater.registerTimeseriesCallbacks({
      ...IDLE_TIMESERIES,
      onInit: () => events.push('chart opened'),
      onSearch: () => events.push('chart searched')
    });
    return { client, events };
  }

  it('asks for the whole flamegraph alongside the timeseries when the recording fits the window', () => {
    const { client } = opening(ONE_HOUR_MILLIS, null);

    expect(client.timeseries.map(request => request.asked)).toEqual([null]);
    expect(client.flamegraphs.map(request => request.asked)).toEqual([null]);
  });

  it('waits for the timeseries of a recording longer than the window', async () => {
    const { client } = opening(3 * ONE_HOUR_MILLIS, null);

    expect(client.flamegraphs).toEqual([]);

    client.timeseries[0].resolve(TIMESERIES);
    await settled();

    expect(client.flamegraphs.map(request => request.asked)).toEqual([
      new TimeRange(0, 3_600_000, false)
    ]);
  });

  it('waits for the timeseries when the duration of the recording is unknown', () => {
    const { client } = opening(null, null);

    expect(client.timeseries).toHaveLength(1);
    expect(client.flamegraphs).toEqual([]);
  });

  it('asks for a linked window alongside the timeseries', () => {
    const { client } = opening(null, { timeRange: LINKED_RANGE, search: null });

    expect(client.flamegraphs.map(request => request.asked)).toEqual([LINKED_RANGE]);
  });

  it('applies a linked search only once the chart has opened', async () => {
    const { client, events } = opening(ONE_HOUR_MILLIS, { timeRange: null, search: SEARCH });

    client.flamegraphs[0].resolve(FLAMEGRAPH);
    await settled();

    expect(events).toEqual(['flamegraph drawn']);

    client.timeseries[0].resolve(TIMESERIES);
    await settled();
    client.timeseries[1].resolve(TIMESERIES);
    await settled();

    expect(client.timeseries[1].asked).toBe(SEARCH);
    expect(events).toEqual([
      'flamegraph drawn',
      'chart opened',
      `search ${SEARCH}`,
      'chart searched'
    ]);
  });
});
