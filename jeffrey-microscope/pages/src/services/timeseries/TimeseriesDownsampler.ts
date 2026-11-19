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

/**
 * Peak-preserving downsampling for chart series given as `[x, y]` tuples in raw data units.
 *
 * Timeseries coming from the backend are seeded with a zero for every second of the recording,
 * so a sparse metric is mostly zeros: a 10-hour profile with 22 VM operations is ~36k points of
 * which 22 are non-zero. Naive every-nth-point sampling *discards* everything that does not land
 * on a multiple of the step, so those 22 spikes disappear entirely and the chart renders flat.
 * Taking the highest value in each bucket instead means a spike can never be sampled away — the
 * bucket it falls into carries it.
 *
 * The emitted x is the x of the bucket's *first* point rather than that of its peak. That keeps
 * the output x-grid a pure function of `(data.length, maxPoints)`, so all series of equal length
 * decimate onto an identical grid. Stacked charts and the shared brush axis depend on that:
 * ApexCharts stacks by data-point index, and the chart component sums series index-by-index when
 * it computes axis maxima. Picking each series' own argmax would desynchronise those grids. The
 * resulting x displacement is at most one bucket — sub-pixel at any realistic chart width.
 */
export default class TimeseriesDownsampler {
  static downsamplePeaks(data: number[][], maxPoints: number): number[][] {
    if (maxPoints <= 0 || data.length <= maxPoints) {
      return data;
    }

    const bucketSize = Math.ceil(data.length / maxPoints);
    const downsampled: number[][] = [];

    for (let start = 0; start < data.length; start += bucketSize) {
      const end = Math.min(start + bucketSize, data.length);
      let peak = data[start][1];
      for (let i = start + 1; i < end; i++) {
        if (data[i][1] > peak) {
          peak = data[i][1];
        }
      }
      downsampled.push([data[start][0], peak]);
    }

    return downsampled;
  }
}
