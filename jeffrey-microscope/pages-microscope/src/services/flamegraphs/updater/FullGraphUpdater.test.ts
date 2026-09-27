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
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import Serie from '@/services/timeseries/model/Serie';
import TimeRange from '@/services/api/model/TimeRange';
import BothGraphData from '@/services/api/model/BothGraphData';

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
const FLAMEGRAPH = new FlamegraphData(0, []);
const LINKED_RANGE = new TimeRange(10_000, 20_000, false);
const SEARCH = 'OrderService';

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

function register({ updater, searched }: Harness, timeseries: boolean): void {
  const none = () => {};
  updater.registerFlamegraphCallbacks(
    none,
    none,
    none,
    expression => searched.push(expression),
    none,
    none,
    none
  );
  if (timeseries) {
    updater.registerTimeseriesCallbacks(none, none, none, none, none, none, none);
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

describe('FullGraphUpdater keeps the range the graph is drawn over', () => {
  const ZOOMED = new TimeRange(30_000, 45_000, false);

  it('is the linked window once a link opened the graph on one', async () => {
    const graph = harness(true);
    graph.updater.openAt({ timeRange: LINKED_RANGE, search: null });
    register(graph, true);
    await settled();

    expect(graph.updater.currentTimeRange()).toEqual(LINKED_RANGE);
  });

  it('is the opening zoom without a link', async () => {
    const graph = harness(true);
    register(graph, true);
    await settled();

    expect(graph.updater.currentTimeRange()).toEqual(new TimeRange(0, 3_600_000, false));
  });

  it('follows a zoom the reader makes, and a reset back to the whole recording', async () => {
    const graph = harness(true);
    register(graph, true);
    await settled();

    graph.updater.updateWithZoom(ZOOMED);
    await settled();
    expect(graph.updater.currentTimeRange()).toEqual(ZOOMED);

    graph.updater.resetZoom();
    await settled();
    expect(graph.updater.currentTimeRange()).toBeNull();
  });

  it('is the whole recording when there is no timeseries and no link', async () => {
    const graph = harness(false);
    register(graph, false);
    await settled();

    expect(graph.updater.currentTimeRange()).toBeNull();
  });
});
