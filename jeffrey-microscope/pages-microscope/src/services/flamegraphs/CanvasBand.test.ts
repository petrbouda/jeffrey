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
import CanvasBand, { type GraphViewport } from './CanvasBand';

const ROW = 20;
const WINDOW = 1000;

/** A graph of the given rows whose top edge sits `graphTop` pixels below the window's top. */
function viewport(rows: number, graphTop: number): GraphViewport {
  return { graphHeight: rows * ROW, rowHeight: ROW, graphTop, windowHeight: WINDOW };
}

describe('CanvasBand', () => {
  it('holds a graph shorter than the window whole', () => {
    const band = CanvasBand.around(viewport(60, 150));

    expect(band.top).toBe(0);
    expect(band.bottom).toBe(60 * ROW);
  });

  it('holds the visible rows of a deep graph plus a window height on either side', () => {
    // 900 rows, scrolled so the window shows the graph from 5000px to 6000px
    const band = CanvasBand.around(viewport(900, -5000));

    expect(band.top).toBe(4000);
    expect(band.bottom).toBe(7000);
  });

  it('aligns the band to whole rows', () => {
    const band = CanvasBand.around(viewport(900, -5010));

    expect(band.top % ROW).toBe(0);
    expect(band.bottom % ROW).toBe(0);
    expect(band.top).toBeLessThanOrEqual(5010 - WINDOW);
    expect(band.bottom).toBeGreaterThanOrEqual(5010 + 2 * WINDOW);
  });

  it('stops at the end of the graph', () => {
    const band = CanvasBand.around(viewport(900, -17500));

    expect(band.bottom).toBe(900 * ROW);
  });

  it('covers the window until it scrolls past the margin', () => {
    const band = CanvasBand.around(viewport(900, -5000));

    expect(band.covers(viewport(900, -5900))).toBe(true);
    expect(band.covers(viewport(900, -6100))).toBe(false);
    expect(band.covers(viewport(900, -3900))).toBe(false);
  });

  it('draws the first rows of a graph that is not yet scrolled into view', () => {
    const band = CanvasBand.around(viewport(900, 3000));

    expect(band.top).toBe(0);
    expect(band.bottom).toBe(WINDOW);
    expect(band.covers(viewport(900, 2000))).toBe(true);
  });
});
