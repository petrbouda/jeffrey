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

import TimeRange from '@/services/api/model/TimeRange';
import TimeConverter from '@/services/timeseries/TimeConverter';

/** A span of the chart, in the chart's data time unit. */
export interface VisibleWindow {
  start: number;
  end: number;
}

/**
 * The window a timeseries chart opens on.
 * <p>
 * A window a link named wins, so the brush marks the part of the recording the flamegraph beside it
 * is drawn from; it arrives as the relative millisecond range of a flamegraph request and is kept
 * inside the data. Without one — or when it misses the data entirely — the chart opens on the
 * configured visible span from the start of the data, as it always has.
 *
 * @param data         the first and last timestamp of the data, in the chart's data time unit
 * @param visibleRange the configured visible span, in the same unit
 * @param linked       the window a link named, or null
 */
export function initialVisibleWindow(
  data: { min: number; max: number },
  visibleRange: number,
  linked: TimeRange | null,
  converter: TimeConverter
): VisibleWindow {
  if (linked != null) {
    const start = Math.max(converter.fromChartTime(linked.start), data.min);
    const end = Math.min(converter.fromChartTime(linked.end), data.max);
    if (end > start) {
      return { start, end };
    }
  }
  const span = Math.min(visibleRange, data.max - data.min);
  return { start: data.min, end: data.min + span };
}
